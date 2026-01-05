(ns nextplace.discovery
  "Discovery engine for auto-discovering places and activities by location.
   Core business logic for Flows 1 and 2."
  (:require [clj-http.client :as http]
            [clojure.data.json :as json]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [integrant.core :as ig]
            [nextplace.availability :as availability]
            [nextplace.events :as events]
            [nextplace.time :as time]
            [nextplace.venues :as venues])
  (:import [java.net URLEncoder]
           [java.time LocalDate LocalDateTime]
           [java.time.format DateTimeFormatter]))

;; Integrant component state
(defonce ^:private discovery-db (atom nil))

;; Activity data loaded from EDN
(defonce ^:private activities-data (atom nil))

(defn- load-activities!
  "Load activity data from EDN resource file."
  []
  (if-let [resource (io/resource "activities.edn")]
    (let [data (edn/read-string (slurp resource))]
      (reset! activities-data data)
      (let [inherent      (or (:inherent-activities data) {})
            venue-count   (reduce + (map count (vals inherent)))
            weather-count (count (:weather-activities data))]
        (log/info "Loaded activities:" venue-count "venue activities,"
                  weather-count "weather activities,"
                  (count inherent) "venue types")
        data))
    (throw (ex-info "activities.edn not found in resources" {}))))

(defn ensure-activities-loaded!
  "Ensure activities are loaded (for test usage).
   Safe to call multiple times - only loads once."
  []
  (when-not @activities-data
    (load-activities!)))

(defmethod ig/init-key :nextplace/discovery [_ {:keys [db]}]
  (reset! discovery-db db)
  (load-activities!)
  (log/info "Discovery component initialized with db and activities")
  {:db db})

(defmethod ig/halt-key! :nextplace/discovery [_ _]
  (reset! discovery-db nil)
  (log/info "Discovery component stopped"))

(def ^:private http-opts
  {:socket-timeout     15000
   :connection-timeout 5000
   :throw-exceptions   false})

;; Maximum search radius in meters (5km to prevent Overpass timeout)
(def ^:private max-search-radius 5000)

(defn- url-encode [s]
  (URLEncoder/encode (str s) "UTF-8"))

;; Geocoding via OpenStreetMap Nominatim (free, no API key)

(defn geocode
  "Geocode a location string to coordinates using OSM Nominatim.
   Biased to USA results for MVP (South SF Bay Area focus).
   Returns {:lat :lng :formatted_address :bounds :input} or nil."
  [location-str]
  (let [;; Add USA context for bare zipcodes
        query    (if (re-matches #"^\d{5}$" location-str)
                   (str location-str ", USA")
                   location-str)
        url      (str "https://nominatim.openstreetmap.org/search"
                      "?q=" (url-encode query)
                      "&format=json"
                      "&limit=1"
                      "&addressdetails=1"
                      "&countrycodes=us")
        response (http/get url (merge http-opts
                                      {:headers {"User-Agent" "Nextplace/1.0"}}))]
    (when (= 200 (:status response))
      (let [results (json/read-str (:body response) :key-fn keyword)]
        (when (seq results)
          (let [result (first results)]
            {:lat               (Double/parseDouble (:lat result))
             :lng               (Double/parseDouble (:lon result))
             :formatted_address (:display_name result)
             :bounds            (when-let [bb (:boundingbox result)]
                                  {:southwest {:lat (Double/parseDouble (nth bb 0))
                                               :lng (Double/parseDouble (nth bb 2))}
                                   :northeast {:lat (Double/parseDouble (nth bb 1))
                                               :lng (Double/parseDouble (nth bb 3))}})
             :input             location-str}))))))

;; Overpass API for venue discovery

(defn- overpass-query
  "Execute an Overpass API query"
  [query]
  (let [url      "https://overpass-api.de/api/interpreter"
        response (http/post url (merge http-opts
                                       {:form-params {:data query}}))]
    (when (= 200 (:status response))
      (json/read-str (:body response) :key-fn keyword))))

(defn- fetch-venues-from-overpass
  "Fetch venues from Overpass API (internal, no caching)."
  [lat lng radius]
  (let [query (str "[out:json][timeout:30];"
                   "("
                   ;; Parks and nature
                   "way[\"leisure\"=\"park\"](around:" radius "," lat "," lng ");"
                   "relation[\"leisure\"=\"park\"](around:" radius "," lat "," lng ");"
                   "way[\"leisure\"=\"nature_reserve\"](around:" radius "," lat "," lng ");"
                   "way[\"leisure\"=\"garden\"](around:" radius "," lat "," lng ");"
                   "way[\"leisure\"=\"dog_park\"](around:" radius "," lat "," lng ");"
                   ;; Recreation
                   "way[\"landuse\"=\"recreation_ground\"](around:" radius "," lat "," lng ");"
                   "way[\"leisure\"=\"playground\"](around:" radius "," lat "," lng ");"
                   "way[\"leisure\"=\"sports_centre\"](around:" radius "," lat "," lng ");"
                   ;; Natural features
                   "way[\"natural\"=\"beach\"](around:" radius "," lat "," lng ");"
                   "node[\"tourism\"=\"viewpoint\"](around:" radius "," lat "," lng ");"
                   ;; Trails
                   "way[\"highway\"=\"path\"][\"name\"](around:" radius "," lat "," lng ");"
                   "way[\"highway\"=\"footway\"][\"name\"](around:" radius "," lat "," lng ");"
                   "relation[\"route\"=\"hiking\"](around:" radius "," lat "," lng ");"
                   ;; Community spaces
                   "way[\"amenity\"=\"community_centre\"](around:" radius "," lat "," lng ");"
                   "node[\"amenity\"=\"community_centre\"](around:" radius "," lat "," lng ");"
                   ;; Plazas and public squares
                   "way[\"place\"=\"square\"](around:" radius "," lat "," lng ");"
                   "way[\"leisure\"=\"plaza\"](around:" radius "," lat "," lng ");"
                   ;; Cafes and social spots (for meetups)
                   "node[\"amenity\"=\"cafe\"](around:" radius "," lat "," lng ");"
                   "way[\"amenity\"=\"cafe\"](around:" radius "," lat "," lng ");"
                   ;; Libraries (community events)
                   "node[\"amenity\"=\"library\"](around:" radius "," lat "," lng ");"
                   "way[\"amenity\"=\"library\"](around:" radius "," lat "," lng ");"
                   ;; Museums and cultural
                   "node[\"tourism\"=\"museum\"](around:" radius "," lat "," lng ");"
                   "way[\"tourism\"=\"museum\"](around:" radius "," lat "," lng ");"
                   ");"
                   "out body;")]
    (when-let [result (overpass-query query)]
      (->> (:elements result)
           (filter #(get-in % [:tags :name]))
           (map (fn [el]
                  (let [tags (:tags el)]
                    {:name          (get tags :name)
                     :type          (or (get tags :leisure)
                                        (get tags :amenity)
                                        (get tags :tourism)
                                        (get tags :natural)
                                        (get tags :landuse)
                                        (get tags :highway)
                                        (get tags :place)
                                        "venue")
                     :cuisine       (get tags :cuisine)
                     :website       (get tags :website)
                     :opening_hours (get tags :opening_hours)
                     :dog           (= "yes" (get tags :dog))
                     :wheelchair    (get tags :wheelchair)
                     :osm_id        (:id el)
                     :osm_type      (:type el)})))
           (distinct)
           (sort-by :name)))))

(defn find-venues
  "Find all venue types near coordinates for activity suggestions.
   Uses caching with intelligent deduplication.
   Returns a list of {:name :type :cuisine :website :dog :wheelchair :osm_id :osm_type}."
  ([geo] (find-venues nil geo))
  ([db-conn {:keys [lat lng bounds]}]
   ;; Cap radius to prevent Overpass timeout on large cities
   (let [radius (min max-search-radius
                     (if bounds
                       (let [ne-lat (get-in bounds [:northeast :lat])
                             sw-lat (get-in bounds [:southwest :lat])]
                         (max 2000 (* 111000 (- ne-lat sw-lat))))
                       max-search-radius))]
     ;; Check cache first if db is provided
     (if-let [cached (and db-conn (venues/get-cached-venues db-conn lat lng))]
       (do
         (log/debug "Using cached venues for" lat lng)
         cached)
       ;; Fetch from Overpass
       (when-let [raw-venues (fetch-venues-from-overpass lat lng radius)]
         (if db-conn
           ;; Cache and deduplicate
           (venues/cache-venues! db-conn lat lng raw-venues)
           ;; Just deduplicate without caching
           (venues/deduplicate-venues raw-venues)))))))

;; Weather from NWS API

(defn fetch-weather
  "Fetch current weather conditions from NWS API.
   Returns {:temperature :temperature_unit :wind_speed :wind_direction
            :short_forecast :detailed :is_daytime :name} or nil."
  [{:keys [lat lng]}]
  (let [points-url (str "https://api.weather.gov/points/" lat "," lng)
        points-res (http/get points-url (merge http-opts
                                               {:headers {"User-Agent" "Nextplace/1.0"
                                                          "Accept"     "application/json"}}))]
    (when (= 200 (:status points-res))
      (let [points-data  (json/read-str (:body points-res) :key-fn keyword)
            forecast-url (get-in points-data [:properties :forecast])]
        (when forecast-url
          (let [forecast-res (http/get forecast-url
                                       (merge http-opts
                                              {:headers {"User-Agent" "Nextplace/1.0"
                                                         "Accept"     "application/json"}}))]
            (when (= 200 (:status forecast-res))
              (let [forecast-data (json/read-str (:body forecast-res) :key-fn keyword)
                    periods       (get-in forecast-data [:properties :periods])
                    current       (first periods)]
                {:temperature      (:temperature current)
                 :temperature_unit (:temperatureUnit current)
                 :wind_speed       (:windSpeed current)
                 :wind_direction   (:windDirection current)
                 :short_forecast   (:shortForecast current)
                 :detailed         (:detailedForecast current)
                 :is_daytime       (:isDaytime current)
                 :name             (:name current)}))))))))

;; Activity pairing based on weather and venue type

(defn- safe-temp
  "Safely get temperature with default"
  [w]
  (or (:temperature w) 0))

(defn- safe-forecast
  "Safely get forecast string lowercased"
  [w]
  (str/lower-case (str (:short_forecast w))))

(defn- safe-wind
  "Safely get wind speed string"
  [w]
  (str (:wind_speed w)))

(defn- no-rain?
  "Check if forecast doesn't include rain"
  [w]
  (not (str/includes? (safe-forecast w) "rain")))

(defn- comfortable-temp?
  "Check if temperature is comfortable for outdoor activity"
  [w min-temp max-temp]
  (and (>= (safe-temp w) min-temp)
       (<= (safe-temp w) max-temp)))

;; Activity moods for time-of-day filtering
;; :relaxing - unwinding, low-key
;; :social - with others, interactive
;; :active - physical, energizing
;; :focused - concentration, work/study
;; :creative - artistic, expressive

;; Accessor functions for loaded activity data
(defn inherent-activities
  "Get inherent activities (always available) from loaded data."
  []
  (or (:inherent-activities @activities-data) {}))

(defn scheduled-event-types
  "Get scheduled event templates that require verification."
  []
  (or (:scheduled-event-types @activities-data) []))

;; Alias for backward compatibility
(def venue-activities inherent-activities)

(defn- conditions->match-fn
  "Convert declarative conditions map to a match function."
  [conditions]
  (fn [w _]
    (let [forecast (safe-forecast w)
          wind     (safe-wind w)
          temp     (safe-temp w)]
      (and
       ;; Fallback always matches
       (or (:fallback conditions)
           (and
            ;; Temperature range
            (if-let [min-t (:temp-min conditions)]
              (>= temp min-t) true)
            (if-let [max-t (:temp-max conditions)]
              (<= temp max-t) true)
            ;; No rain check
            (if (:no-rain conditions)
              (not (str/includes? forecast "rain")) true)
            ;; Forecast contains check (any of the terms)
            (if-let [terms (:forecast-contains conditions)]
              (some #(str/includes? forecast %) terms) true)
            ;; Wind contains check
            (if-let [term (:wind-contains conditions)]
              (str/includes? wind term) true)
            ;; Wind not contains check
            (if-let [term (:wind-not-contains conditions)]
              (not (str/includes? wind term)) true)
            ;; Daytime check
            (if (:daytime conditions)
              (:is_daytime w) true)
            ;; Not daytime check
            (if (:not-daytime conditions)
              (not (:is_daytime w)) true)
            ;; Or not daytime (for conditions like "cloudy OR not daytime")
            (if (:or-not-daytime conditions)
              true true)))))))

(defn weather-activities
  "Get weather-based activities with match functions generated from conditions."
  []
  (->> (or (:weather-activities @activities-data) [])
       (map (fn [activity]
              (assoc activity :match-fn (conditions->match-fn (:conditions activity)))))))

(defn time-mood-preferences
  "Get preferred activity moods based on time context."
  []
  (or (:time-mood-preferences @activities-data)
      {:weekday-morning   #{:active :focused}
       :weekday-lunch     #{:social :relaxing}
       :weekday-afternoon #{:focused :active}
       :weekday-evening   #{:relaxing :social :active :creative}
       :weekend-morning   #{:active :relaxing}
       :weekend-afternoon #{:social :relaxing :creative :active}
       :weekend-evening   #{:social :relaxing}}))

(defn time-context
  "Determine time context from hour and day of week.
   Returns a keyword like :weekday-evening or :weekend-morning."
  [hour day-of-week]
  (let [is-weekend (or (= day-of-week java.time.DayOfWeek/SATURDAY)
                       (= day-of-week java.time.DayOfWeek/SUNDAY))
        period     (cond
                     (< hour 12) :morning
                     (< hour 14) :lunch
                     (< hour 17) :afternoon
                     :else :evening)]
    (keyword (str (if is-weekend "weekend" "weekday") "-" (name period)))))

(defn preferred-moods
  "Get preferred moods for a given time context."
  [hour day-of-week]
  (get (time-mood-preferences) (time-context hour day-of-week)
       #{:relaxing :social}))

(defn indoor-venue-types
  "Get venue types that are primarily indoors."
  []
  (or (:indoor-venue-types @activities-data)
      #{"cafe" "library" "museum" "community_centre" "sports_centre"}))

(defn match-activity
  "Find the best activity for weather conditions and venue type.
   Considers venue-specific activities and weather conditions."
  ([weather]
   (match-activity weather nil))
  ([weather venue]
   (let [venue-type   (:type venue)
         venue-acts-m (venue-activities)
         weather-acts (weather-activities)
         indoor-types (indoor-venue-types)]
     (cond
       ;; Indoor venues - pick from venue-specific activities (weather doesn't matter much)
       (and venue-type (indoor-types venue-type))
       (let [activities (get venue-acts-m venue-type)]
         (if (seq activities)
           (rand-nth activities)
           {:activity    "Explore"
            :description (str "Check out " (:name venue))
            :duration    "1-2 hours"}))

       ;; Outdoor venue with venue-specific activities - blend with weather
       (and venue-type (get venue-acts-m venue-type))
       (let [venue-acts   (get venue-acts-m venue-type)
             matched-acts (filter #((:match-fn %) weather venue) weather-acts)]
         ;; 50/50 chance of venue-specific vs weather-based activity
         (if (and (seq venue-acts) (< (rand) 0.5))
           (rand-nth venue-acts)
           (or (first matched-acts)
               (last weather-acts))))

       ;; Pure weather-based matching for outdoor venues
       :else
       (or (first (filter #((:match-fn %) weather venue) weather-acts))
           (last weather-acts))))))

;; =============================================================================
;; Suggestion Generation (Core Flow 1 Logic)
;; =============================================================================
;;
;; The system takes ONLY location (from GPS) and uses current time (from clock).
;; It decides what to suggest - user has zero input.
;; Returns ONE suggestion - no options, no browsing.

(def ^:private min-hours-ahead 4)
(def ^:private max-hours-ahead 48)

(defn- suggestion-window
  "Calculate the 4-48 hour window for suggestions.
   If reference-time is provided, uses that as 'now' (for testing/validation).
   Returns {:earliest LocalDateTime, :latest LocalDateTime, :dates [LocalDate...]}."
  ([] (suggestion-window nil))
  ([reference-time]
   (let [now      (or reference-time (LocalDateTime/now))
         earliest (.plusHours now min-hours-ahead)
         latest   (.plusHours now max-hours-ahead)
         ;; Get all dates that fall within the window
         dates    (loop [d   (.toLocalDate earliest)
                         end (.toLocalDate latest)
                         acc []]
                    (if (.isAfter d end)
                      acc
                      (recur (.plusDays d 1) end (conj acc d))))]
     {:earliest earliest
      :latest   latest
      :dates    dates})))

(defn generate-event-time
  "Generate a suggested event time within the 4-48 hour window.
   If reference-time is provided, uses that as 'now' (for testing/validation).
   Returns {:start LocalDateTime, :formatted String}."
  ([] (generate-event-time nil))
  ([reference-time]
   (let [now        (or reference-time (LocalDateTime/now))
         ;; Add 4-8 hours for same-day, or next morning
         hours-add  (if (< (.getHour now) 14)
                      (+ 4 (rand-int 4))   ;; Same day afternoon
                      (+ 16 (rand-int 4))) ;; Tomorrow morning
         start-time (.plusHours now hours-add)
         ;; Round to nearest 30 min
         minute     (.getMinute start-time)
         rounded    (if (< minute 30)
                      (.withMinute start-time 0)
                      (.withMinute start-time 30))
         formatter  (java.time.format.DateTimeFormatter/ofPattern "EEEE h:mm a")]
     {:start     rounded
      :formatted (.format rounded formatter)})))

(defn- search-scheduled-events
  "Search for verified scheduled events near location within the suggestion window.
   RATE LIMITED: Searches generically for event types in the area, then matches
   against known venues. This approach makes fewer searches and finds more events.
   If reference-time is provided, uses that as 'now' (for testing/validation).
   Returns the first verified event or nil."
  ([db venues] (search-scheduled-events db venues nil))
  ([db venues reference-time]
   (let [{:keys [dates]} (suggestion-window reference-time)
         event-templates (scheduled-event-types)
         ;; Build venue name lookup for matching search results
         venue-names     (set (map (comp str/lower-case :name) venues))
         venue-by-name   (into {} (map (fn [v] [(str/lower-case (:name v)) v]) venues))]
     (when (seq event-templates)
       ;; Pick ONE random event type to search for (rate limiting)
       (let [template    (rand-nth event-templates)
             target-date (rand-nth dates)
             ;; Generic area search instead of venue-specific
             search-term (first (:search-terms template))]
         (log/info "Searching for" search-term "events on" (.toString target-date))
         ;; Use events/verify-event but with a synthetic "area" venue
         ;; to do a broad search, then match results against known venues
         (let [area-query      (str search-term " " (.toString target-date))
               ;; For now, just pick a random matching venue to verify
               ;; Future: parse search results to find mentioned venues
               matching-venues (filter #(contains? (:venue-types template) (:type %))
                                       venues)]
           (when (seq matching-venues)
             (let [venue (rand-nth (take 5 matching-venues))]
               (log/info "Verifying at venue:" (:name venue))
               (events/verify-event db venue template target-date)))))))))

(defn- format-scheduled-event-as-suggestion
  "Convert a verified scheduled event to the standard suggestion format."
  [event geo weather]
  (let [formatter (java.time.format.DateTimeFormatter/ofPattern "EEEE h:mm a")]
    {:place    (:venue event)
     :activity {:activity    (:activity event)
                :description (:description event)
                :duration    (:duration event)
                :mood        (:mood event)}
     :weather  weather
     :time     {:start     (:start-time event)
                :formatted (.format (:start-time event) formatter)}
     :location geo
     :verified true
     :source   :scheduled}))

(defn- generate-inherent-suggestion
  "Generate a suggestion using inherent activities (always available).
   These are guaranteed to be possible - no verification needed.
   If reference-time is provided, uses that as 'now' (for testing/validation)."
  ([geo weather venues] (generate-inherent-suggestion geo weather venues nil))
  ([geo weather venues reference-time]
   (let [place    (rand-nth (take 20 venues))
         activity (match-activity weather place)
         time     (generate-event-time reference-time)]
     {:place    place
      :activity (dissoc activity :match-fn)
      :weather  weather
      :time     time
      :location geo
      :verified true
      :source   :inherent})))

(defn generate-suggestion
  "Generate THE suggestion for a location.

   This is the core Flow 1 function. Takes ONLY location (from GPS).
   Current time comes from system clock. User has zero input.
   Returns ONE suggestion - either a verified scheduled event or an inherent activity.

   The system:
   1. Calculates the 4-48 hour suggestion window
   2. Fetches weather for the location
   3. Finds venues nearby
   4. Searches for verified scheduled events in the window (if db available)
   5. If a scheduled event is found, returns that
   6. Otherwise, generates an inherent activity suggestion

   Optional reference-time parameter (LocalDateTime) allows console/testing to
   simulate a different 'now' for validation purposes.

   Returns {:place :activity :weather :time :location :verified :source} or nil."
  ([geo] (generate-suggestion geo nil))
  ([geo reference-time]
   (let [db @discovery-db]
     (when-let [weather (fetch-weather geo)]
       (when-let [venues (seq (find-venues db geo))]
         ;; Try to find a verified scheduled event first (only if db is available)
         (if-let [scheduled-event (and db (search-scheduled-events db venues reference-time))]
           (do
             (log/info "Found scheduled event:" (:activity scheduled-event)
                       "at" (get-in scheduled-event [:venue :name]))
             (format-scheduled-event-as-suggestion scheduled-event geo weather))
           ;; Fall back to inherent activity
           (do
             (log/debug "No scheduled events found, generating inherent activity")
             (generate-inherent-suggestion geo weather venues reference-time))))))))
