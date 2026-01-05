(ns nextplace.search
  "Web search utilities for discovering opportunities.
   Used by discovery flows to find volunteer opportunities, events, etc."
  (:require [clj-http.client :as http]
            [clojure.string :as str]
            [clojure.tools.logging :as log])
  (:import [java.net URLEncoder]))

;; =============================================================================
;; HTTP Configuration
;; =============================================================================

(def ^:private http-opts
  {:socket-timeout     10000
   :connection-timeout 5000
   :throw-exceptions   false
   :headers            {"User-Agent" "Mozilla/5.0"}})

(defn- url-encode [s]
  (URLEncoder/encode (str s) "UTF-8"))

;; =============================================================================
;; DuckDuckGo Search
;; =============================================================================

(defn ddg-search
  "Search DuckDuckGo and return results as seq of {:url :title}."
  [query]
  (let [url      (str "https://html.duckduckgo.com/html/?q=" (url-encode query))
        response (http/get url http-opts)]
    (when (= 200 (:status response))
      (let [body (:body response)]
        (->> (re-seq #"class=\"result__a\"[^>]*href=\"([^\"]+)\"[^>]*>([^<]+)" body)
             (take 10)
             (map (fn [[_ result-url title]]
                    {:url   (str/replace result-url #"//duckduckgo.com/l/\?uddg=" "")
                     :title (str/trim title)})))))))

;; =============================================================================
;; Opportunity Discovery
;; =============================================================================

(def ^:private discovery-categories
  "Categories of opportunities to search for with their query suffixes."
  [["nonprofit volunteer opportunities" :volunteer]
   ["park cleanup community service" :park_cleanup]
   ["animal rescue volunteer humane society" :animal_rescue]
   ["walking group hiking meetup easy" :walking_groups]
   ["open streets community event festival" :community_events]])

(defn search-opportunities
  "Search all discovery categories for a location.
   Returns map of category -> results, plus :venues key with passed venues.
   Rate-limited with 5 second delay between searches to avoid DuckDuckGo blocking."
  [location-name venues]
  (let [results (atom {:venues venues})]

    ;; Base location searches - 5 second delay to avoid rate limiting
    (doseq [[suffix category] discovery-categories]
      (log/debug "Searching category:" (name category))
      (let [query          (str location-name " " suffix)
            search-results (ddg-search query)]
        (swap! results assoc category search-results)
        (Thread/sleep 5000)))

    ;; Venue-specific searches for events - 5 second delay
    (when (seq venues)
      (log/debug "Searching venue events...")
      (let [venue-events (atom [])]
        (doseq [venue (take 3 venues)] ;; Reduced from 5 to 3
          (let [query          (str (:name venue) " " location-name " cleanup meetup event")
                search-results (ddg-search query)]
            (when (seq search-results)
              (swap! venue-events concat
                     (map #(assoc % :venue (:name venue)) search-results)))
            (Thread/sleep 5000)))
        (swap! results assoc :venue_events (distinct @venue-events))))

    @results))
