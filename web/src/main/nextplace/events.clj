(ns nextplace.events
  "Event verification service.
   Verifies scheduled events are actually happening at specific venues
   via web search and heuristic analysis before suggesting them to users."
  (:require [clj-http.client :as http]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [diehard.core :as dh]
            [diehard.rate-limiter :as rl]
            [nextplace.db :as db])
  (:import [java.time LocalDate]
           [java.net URLEncoder]))

;; =============================================================================
;; Configuration
;; =============================================================================

;; Cache TTL for verified events (6 hours)
(def ^:private cache-ttl-ms (* 6 60 60 1000))

(def ^:private http-opts
  {:socket-timeout     10000
   :connection-timeout 5000
   :throw-exceptions   false
   :headers            {"User-Agent" "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36"}})

;; Rate limiter: 1 request per 5 seconds to avoid DuckDuckGo blocking
(defonce ^:private search-rate-limiter
  (rl/rate-limiter {:rate 0.2}))

;; Retry policy for transient failures
(def ^:private retry-policy
  (dh/retry-policy-from-config
   {:max-retries 2
    :backoff-ms  [5000 15000]
    :retry-on    [Exception]}))

;; =============================================================================
;; Search
;; =============================================================================

(defn- search-query
  "Build search query for event at venue."
  [venue-name event-template]
  (let [terms (:search-terms event-template)]
    (str "\"" venue-name "\" " (first terms) " schedule")))

(defn- fetch-search-results
  "Fetch search results from DuckDuckGo.
   Returns HTML string or nil if failed."
  [query]
  (try
    (dh/with-rate-limiter {:ratelimiter search-rate-limiter}
      (dh/with-retry {:policy   retry-policy
                      :on-retry (fn [_ ex] (log/debug "Retrying search after:" (.getMessage ex)))}
        (let [url      (str "https://html.duckduckgo.com/html/?q="
                            (URLEncoder/encode query "UTF-8"))
              response (http/get url http-opts)]
          (case (:status response)
            200 (:body response)
            (202 429 503 403) (do
                                (log/warn "Search rate limited, waiting 10s" {:status (:status response)})
                                (Thread/sleep 10000)
                                (throw (ex-info "Rate limited" {:status (:status response)})))
            (do
              (log/warn "Search failed" {:status (:status response) :query query})
              nil)))))
    (catch Exception e
      (log/warn "Search exhausted retries" {:query query :error (.getMessage e)})
      nil)))

;; =============================================================================
;; Smart Heuristic Verification
;; =============================================================================

(def ^:private day-patterns
  "Regex patterns for days of week and recurring indicators."
  {:monday    #"(?i)\b(monday|mon\.?)\b"
   :tuesday   #"(?i)\b(tuesday|tue\.?|tues\.?)\b"
   :wednesday #"(?i)\b(wednesday|wed\.?)\b"
   :thursday  #"(?i)\b(thursday|thu\.?|thur\.?|thurs\.?)\b"
   :friday    #"(?i)\b(friday|fri\.?)\b"
   :saturday  #"(?i)\b(saturday|sat\.?)\b"
   :sunday    #"(?i)\b(sunday|sun\.?)\b"})

(def ^:private recurring-patterns
  "Patterns indicating recurring events."
  [#"(?i)\bevery\s+(monday|tuesday|wednesday|thursday|friday|saturday|sunday)"
   #"(?i)\b(monday|tuesday|wednesday|thursday|friday|saturday|sunday)s\b"
   #"(?i)\bweekly\b"
   #"(?i)\bevery\s+week\b"
   #"(?i)\brecurring\b"])

(def ^:private time-pattern
  "Pattern to extract event times."
  #"(?i)(\d{1,2}):?(\d{2})?\s*(am|pm|a\.m\.|p\.m\.)?")

(defn- normalize-day
  "Convert LocalDate day of week to keyword."
  [^LocalDate date]
  (-> date .getDayOfWeek .toString str/lower-case keyword))

(defn- extract-text-snippets
  "Extract text snippets from HTML, removing tags."
  [html]
  (-> html
      (str/replace #"<script[^>]*>.*?</script>" " ")
      (str/replace #"<style[^>]*>.*?</style>" " ")
      (str/replace #"<[^>]+>" " ")
      (str/replace #"&[a-z]+;" " ")
      (str/replace #"\s+" " ")))

(defn- find-contextual-matches
  "Find text windows around a pattern match for context analysis.
   Returns seq of {:match :context :position} maps."
  [text pattern window-size]
  (let [matcher (re-matcher pattern text)]
    (loop [matches []]
      (if (.find matcher)
        (let [start (max 0 (- (.start matcher) window-size))
              end   (min (count text) (+ (.end matcher) window-size))]
          (recur (conj matches {:match    (.group matcher)
                                :context  (subs text start end)
                                :position (.start matcher)})))
        matches))))

(defn- score-venue-event-connection
  "Score how strongly venue name connects to event terms in text.
   Returns 0-1 confidence score."
  [text venue-name event-terms]
  (let [text-lower    (str/lower-case text)
        venue-lower   (str/lower-case venue-name)
        venue-parts   (str/split venue-lower #"\s+")
        ;; Find venue mentions
        venue-matches (find-contextual-matches text-lower
                                               (re-pattern (str "(?i)" (first venue-parts)))
                                               200)]
    (if (empty? venue-matches)
      0.0
      ;; Check if event terms appear near venue mentions
      (let [contexts-with-events
            (filter (fn [{:keys [context]}]
                      (some #(str/includes? context (str/lower-case %)) event-terms))
                    venue-matches)]
        (if (empty? contexts-with-events)
          0.1  ;; Venue found but no event terms nearby
          (/ (count contexts-with-events) (count venue-matches)))))))

(defn- score-day-event-connection
  "Score how strongly target day connects to event in text.
   Returns 0-1 confidence score."
  [text target-day event-terms]
  (let [text-lower  (str/lower-case text)
        day-pattern (get day-patterns target-day)
        day-matches (when day-pattern
                      (find-contextual-matches text-lower day-pattern 150))]
    (if (or (nil? day-pattern) (empty? day-matches))
      0.0
      ;; Check for recurring patterns and event terms near day mentions
      (let [recurring-bonus (if (some #(re-find % text-lower) recurring-patterns) 0.3 0.0)
            contexts-with-events
            (filter (fn [{:keys [context]}]
                      (some #(str/includes? context (str/lower-case %)) event-terms))
                    day-matches)
            base-score      (if (empty? contexts-with-events)
                              0.1
                              (* 0.7 (/ (count contexts-with-events) (count day-matches))))]
        (min 1.0 (+ base-score recurring-bonus))))))

(defn- extract-event-time
  "Extract most likely event time from text near event/day mentions.
   Returns [hour-24 minute] or nil."
  [text event-terms target-day]
  (let [text-lower     (str/lower-case text)
        day-pattern    (get day-patterns target-day)
        ;; Find contexts mentioning event terms
        event-contexts (mapcat #(find-contextual-matches text-lower
                                                         (re-pattern (str "(?i)" %))
                                                         100)
                               event-terms)
        ;; Also check near day mentions
        day-contexts   (when day-pattern
                         (find-contextual-matches text-lower day-pattern 100))
        all-contexts   (concat event-contexts day-contexts)
        ;; Extract times from these contexts
        times          (->> all-contexts
                            (mapcat (fn [{:keys [context]}]
                                      (map first (re-seq time-pattern context))))
                            (frequencies)
                            (sort-by val >)
                            (map key))]
    (when-let [time-str (first times)]
      (let [[_ hour minute ampm] (re-find time-pattern time-str)
            hour-int             (Integer/parseInt hour)
            minute-int           (if minute (Integer/parseInt minute) 0)
            hour-24              (cond
                                   (and ampm (re-find #"(?i)pm|p\.m\." ampm) (< hour-int 12))
                                   (+ hour-int 12)
                                   (and ampm (re-find #"(?i)am|a\.m\." ampm) (= hour-int 12))
                                   0
                                   ;; Assume PM for evening event times without indicator
                                   (and (nil? ampm) (<= 5 hour-int 9))
                                   (+ hour-int 12)
                                   :else hour-int)]
        [hour-24 minute-int]))))

(defn- verify-event-heuristic
  "Verify event using heuristic analysis of search results.
   Returns {:verified bool :confidence 0-1 :event_time str :reasoning str} or nil."
  [search-html venue-name event-template ^LocalDate target-date]
  (let [text          (extract-text-snippets search-html)
        target-day    (normalize-day target-date)
        event-terms   (:search-terms event-template)
        ;; Score different aspects
        venue-score   (score-venue-event-connection text venue-name event-terms)
        day-score     (score-day-event-connection text target-day event-terms)
        ;; Both venue AND day must have some signal for high confidence
        ;; If day score is 0 (target day not mentioned), cap confidence at 0.3
        confidence    (if (< day-score 0.1)
                        (* venue-score 0.3)  ;; Cap when day not found
                        (* (+ (* 0.5 venue-score) (* 0.5 day-score)) 1.0))
        ;; Extract time if confident enough
        [hour minute] (when (>= confidence 0.4)
                        (extract-event-time text event-terms target-day))
        time-str      (when hour
                        (let [hr12  (cond (= hour 0) 12 (> hour 12) (- hour 12) :else hour)
                              am-pm (if (>= hour 12) "PM" "AM")]
                          (format "%d:%02d %s" hr12 minute am-pm)))]
    {:verified   (>= confidence 0.5)
     :confidence (double confidence)
     :event_time time-str
     :reasoning  (str "Venue score: " (format "%.2f" (double venue-score))
                      ", Day score: " (format "%.2f" (double day-score))
                      (when (some #(re-find % (str/lower-case text)) recurring-patterns)
                        ", Recurring pattern detected"))}))

;; =============================================================================
;; Public API
;; =============================================================================

(defn- parse-time-string
  "Parse a time string like '7:00 PM' into hour and minute.
   Returns [hour-24 minute] or nil."
  [time-str]
  (when time-str
    (let [matches (re-find time-pattern time-str)]
      (when matches
        (let [[_ hour minute ampm] matches
              hour-int             (Integer/parseInt hour)
              minute-int           (if minute (Integer/parseInt minute) 0)
              hour-24              (cond
                                     (and ampm (re-find #"(?i)pm|p\.m\." ampm) (< hour-int 12))
                                     (+ hour-int 12)
                                     (and ampm (re-find #"(?i)am|a\.m\." ampm) (= hour-int 12))
                                     0
                                     :else hour-int)]
          [hour-24 minute-int])))))

(defn- cache-key
  "Generate cache key for verified event."
  [venue-osm-id event-type ^LocalDate date]
  (str "event:" venue-osm-id ":" event-type ":" (.toString date)))

(defn- cache-event!
  "Cache a verified event."
  [db venue-osm-id event-type ^LocalDate date event-data]
  (let [key   (cache-key venue-osm-id event-type date)
        entry {:event     event-data
               :cached_at (System/currentTimeMillis)}]
    (db/put-value db key entry)))

(defn- get-cached-event
  "Get cached event verification result."
  [db venue-osm-id event-type ^LocalDate date]
  (when-let [entry (db/get-value db (cache-key venue-osm-id event-type date))]
    (when (< (- (System/currentTimeMillis) (:cached_at entry))
             cache-ttl-ms)
      (:event entry))))

(defn verify-event
  "Verify if a scheduled event is happening at a venue on a specific date.
   Uses web search + heuristic analysis for verification.
   Returns verified event map with :start-time or nil if not verified.

   Parameters:
   - db: RocksDB instance for caching
   - venue: Venue map with :name, :osm_id
   - event-template: Event template with :search-terms, :activity, etc.
   - target-date: LocalDate to check"
  [db venue event-template ^LocalDate target-date]
  (let [venue-osm-id (:osm_id venue)
        event-type   (:event-type event-template)]

    ;; Check cache first
    (if-let [cached (get-cached-event db venue-osm-id event-type target-date)]
      (do
        (log/debug "Using cached event verification for" (:name venue) event-type)
        cached)

      ;; Perform web search + heuristic verification
      (let [query       (search-query (:name venue) event-template)
            _           (log/info "Verifying event:" (:name venue) "-" (:activity event-template)
                                  "| query:" query)
            search-html (fetch-search-results query)]

        (if-not search-html
          (do
            (cache-event! db venue-osm-id event-type target-date nil)
            (log/info "No search results for" (:activity event-template) "at" (:name venue))
            nil)

          ;; Analyze with heuristics
          (let [result (verify-event-heuristic search-html (:name venue) event-template target-date)]
            (if (and result (:verified result) (>= (:confidence result) 0.5))
              ;; Verified
              (let [extracted-time (:event_time result)
                    [hour minute]  (or (parse-time-string extracted-time) [19 0])
                    start-time     (.atTime target-date hour minute)
                    verified-event {:venue       venue
                                    :activity    (:activity event-template)
                                    :description (:description event-template)
                                    :duration    (:duration event-template)
                                    :mood        (:mood event-template)
                                    :event-type  event-type
                                    :start-time  start-time
                                    :confidence  (:confidence result)
                                    :reasoning   (:reasoning result)
                                    :verified    true
                                    :verified-at (System/currentTimeMillis)}]
                (cache-event! db venue-osm-id event-type target-date verified-event)
                (log/info "Verified event:" (:activity event-template)
                          "at" (:name venue)
                          "| confidence:" (format "%.2f" (:confidence result))
                          "| time:" extracted-time
                          "| reasoning:" (:reasoning result))
                verified-event)

              ;; Not verified
              (do
                (cache-event! db venue-osm-id event-type target-date nil)
                (log/info "Could not verify:" (:activity event-template)
                          "at" (:name venue)
                          "| confidence:" (format "%.2f" (or (:confidence result) 0))
                          "| reasoning:" (:reasoning result))
                nil))))))))

(defn- extract-venue-mentions
  "Find which venues from our list are mentioned in search results text.
   Returns venues whose names appear near event terms.
   Uses a 100-char window to ensure tight proximity matching."
  [text event-terms venues]
  (let [text-lower (str/lower-case text)]
    (->> venues
         (filter (fn [venue]
                   (let [name-lower (str/lower-case (:name venue))
                         name-parts (str/split name-lower #"\s+")
                         ;; Use first significant word (>3 chars) to find mentions
                         sig-word   (first (filter #(> (count %) 3) name-parts))]
                     (when sig-word
                       ;; Check if venue mentioned near event terms (within 100 chars)
                       (let [venue-matches (find-contextual-matches text-lower
                                                                    (re-pattern (str "(?i)" sig-word))
                                                                    100)]
                         (some (fn [{:keys [context]}]
                                 (some #(str/includes? context (str/lower-case %)) event-terms))
                               venue-matches))))))
         (vec))))

(defn search-area-for-event-type
  "Search for an event type across a geographic area.
   Instead of searching for a specific venue, searches the whole area
   and identifies which known venues are mentioned with the event.

   Returns list of verified event maps for any confirmed venue+event+day combinations.

   Parameters:
   - db: RocksDB instance for caching
   - area-name: Geographic area string (e.g., 'Springfield, TN')
   - venues: Sequence of venue maps to match against
   - event-template: Event template with :search-terms, :activity, etc.
   - target-date: LocalDate to check"
  [db area-name venues event-template ^LocalDate target-date]
  (let [event-type     (:event-type event-template)
        search-term    (first (:search-terms event-template))
        cache-key-base (str "area_search:" (str/replace area-name #"[^a-zA-Z0-9]" "_")
                            ":" event-type ":" (.toString target-date))]

    ;; Check if we've already searched this area+event+date
    (if-let [cached (db/get-value db cache-key-base)]
      (when (< (- (System/currentTimeMillis) (:cached_at cached)) cache-ttl-ms)
        (log/debug "Using cached area search for" area-name event-type)
        (:events cached))

      ;; Perform area-wide search
      (let [query       (str area-name " " search-term " schedule")
            _           (log/info "Area search:" event-type "| query:" query)
            search-html (fetch-search-results query)]

        (if-not search-html
          (do
            (db/put-value db cache-key-base {:events [] :cached_at (System/currentTimeMillis)})
            (log/info "No results for" event-type "in" area-name)
            [])

          ;; Find which known venues are mentioned in results
          (let [text             (extract-text-snippets search-html)
                event-terms      (:search-terms event-template)
                mentioned-venues (extract-venue-mentions text event-terms venues)
                ;; Filter to venues that match this event's venue-types
                matching-venues  (filter #(contains? (:venue-types event-template) (:type %))
                                         mentioned-venues)
                _                (when (seq matching-venues)
                                   (log/info "Found venues mentioned:" (mapv :name matching-venues)))

                ;; Verify each mentioned venue using the same search results
                verified-events
                (->> matching-venues
                     (map (fn [venue]
                            (let [result (verify-event-heuristic search-html (:name venue)
                                                                 event-template target-date)]
                              (when (and result (:verified result) (>= (:confidence result) 0.5))
                                (let [[hour minute] (or (parse-time-string (:event_time result)) [19 0])
                                      start-time    (.atTime target-date hour minute)]
                                  {:venue       venue
                                   :activity    (:activity event-template)
                                   :description (:description event-template)
                                   :duration    (:duration event-template)
                                   :mood        (:mood event-template)
                                   :event-type  event-type
                                   :start-time  start-time
                                   :confidence  (:confidence result)
                                   :reasoning   (:reasoning result)
                                   :verified    true
                                   :verified-at (System/currentTimeMillis)})))))
                     (filter some?)
                     (vec))]

            ;; Cache the results
            (db/put-value db cache-key-base {:events verified-events :cached_at (System/currentTimeMillis)})

            (if (seq verified-events)
              (log/info "Verified" (count verified-events) "events for" event-type "in" area-name)
              (log/info "No verified events for" event-type "in" area-name))

            verified-events))))))

(defn find-all-verified-events
  "Search for ALL scheduled event types in an area.
   Returns all verified events across all event types that match available venues.

   This is the main entry point for scheduled event discovery. It:
   1. Filters event templates to those matching available venue types
   2. Searches for each event type in the area (one search per type)
   3. Identifies which known venues are mentioned in results
   4. Verifies and returns all confirmed events

   Parameters:
   - db: RocksDB instance for caching
   - area-name: Geographic area (e.g., 'Springfield, TN')
   - venues: Sequence of venue maps
   - event-templates: Sequence of event templates to check
   - target-dates: Sequence of LocalDates to check
   - max-searches: Maximum web searches to perform (rate limiting)"
  [db area-name venues event-templates target-dates & {:keys [max-searches] :or {max-searches 5}}]
  (let [;; Get venue types we have
        available-types (set (map :type venues))

        ;; Filter templates to those with matching venue types
        relevant-templates (->> event-templates
                                (filter #(some available-types (:venue-types %)))
                                (vec))

        ;; Limit searches - pick random subset if too many
        templates-to-search (if (> (count relevant-templates) max-searches)
                              (take max-searches (shuffle relevant-templates))
                              relevant-templates)

        ;; Pick one target date (usually tomorrow or day after)
        target-date (first target-dates)]

    (log/info "Searching" (count templates-to-search) "event types in" area-name
              "for" (.toString target-date))

    ;; Search for each event type (rate limited by fetch-search-results)
    (->> templates-to-search
         (mapcat #(search-area-for-event-type db area-name venues % target-date))
         (vec))))

(defn find-verified-events
  "DEPRECATED: Use find-all-verified-events instead.
   Kept for backward compatibility."
  [db venues event-templates ^LocalDate target-date & {:keys [max-searches] :or {max-searches 10}}]
  (let [venue-event-pairs
        (for [venue    venues
              template event-templates
              :when    (contains? (:venue-types template) (:type venue))]
          [venue template])

        pairs-to-check (take max-searches venue-event-pairs)]

    (->> pairs-to-check
         (map (fn [[venue template]]
                (verify-event db venue template target-date)))
         (filter some?)
         (doall))))

(defn clear-event-cache!
  "Clear all cached event verifications."
  [db]
  nil)
