(ns nextplace.console.discovery
  "Discovery console for auto-discovering opportunities by location.
   Thin wrapper around nextplace.discovery for REPL usage."
  (:require [clj-http.client :as http]
            [clojure.string :as str]
            [nextplace.console.util :as util]
            [nextplace.discovery :as discovery])
  (:import [java.net URLEncoder]))

(defonce ^:private back-ns (atom 'user))
(defonce ^:private current-location (atom nil))

(def ^:private http-opts
  {:socket-timeout     10000
   :connection-timeout 5000
   :throw-exceptions   false})

(defn- url-encode [s]
  (URLEncoder/encode (str s) "UTF-8"))

(defn set-top-level-ns!
  "Set the namespace to return to when calling (back)"
  [ns-sym]
  (reset! back-ns ns-sym))

(defn back
  "Exit back to the top-level namespace"
  []
  (in-ns @back-ns))

(defn commands
  "List available console commands"
  []
  (let [vars         (util/list-public-vars 'nextplace.console.discovery)
        nav-commands {"(back)"     "exit back to initial namespace"
                      "(commands)" "list commands"}]
    (println "\nDiscovery Console Commands:\n")
    (doseq [[fn-str desc] nav-commands]
      (println fn-str)
      (println "   -" desc "\n"))
    (doseq [v vars]
      (println (util/format-command v) "\n")))
  :ok)

;; Web Search (console-specific for admin discovery)

(defn- ddg-search
  "Search DuckDuckGo and return results"
  [query]
  (let [url      (str "https://html.duckduckgo.com/html/?q=" (url-encode query))
        response (http/get url (merge http-opts
                                      {:headers {"User-Agent" "Mozilla/5.0"}}))]
    (when (= 200 (:status response))
      (let [body (:body response)]
        (->> (re-seq #"class=\"result__a\"[^>]*href=\"([^\"]+)\"[^>]*>([^<]+)" body)
             (take 10)
             (map (fn [[_ url title]]
                    {:url   (str/replace url #"//duckduckgo.com/l/\?uddg=" "")
                     :title (str/trim title)})))))))

(defn- search-all-categories
  "Search all discovery categories for a location"
  [location-name venues]
  (let [categories [["nonprofit volunteer opportunities" :volunteer]
                    ["park cleanup community service" :park_cleanup]
                    ["animal rescue volunteer humane society" :animal_rescue]
                    ["walking group hiking meetup easy" :walking_groups]
                    ["open streets community event festival" :community_events]]
        results    (atom {:venues venues})]

    ;; Base location searches
    (doseq [[suffix category] categories]
      (println "  Searching:" (name category))
      (let [query          (str location-name " " suffix)
            search-results (ddg-search query)]
        (swap! results assoc category search-results)
        (Thread/sleep 1000)))

    ;; Venue-specific searches for events
    (when (seq venues)
      (println "  Searching venue events...")
      (let [venue-events (atom [])]
        (doseq [venue (take 5 venues)]
          (let [query   (str (:name venue) " " location-name " cleanup meetup event")
                results (ddg-search query)]
            (when (seq results)
              (swap! venue-events concat
                     (map #(assoc % :venue (:name venue)) results)))
            (Thread/sleep 500)))
        (swap! results assoc :venue_events (distinct @venue-events))))

    @results))

(defn- print-results
  "Print discovery results in readable format"
  [results]
  (println "\n=== Discovery Results ===\n")

  (when-let [venues (:venues results)]
    (println "## VENUES (" (count venues) ")")
    (doseq [{:keys [name type website]} (take 30 venues)]
      (println " -" name (str "(" type ")")
               (when website (str "\n   " website))))
    (println))

  (doseq [[category items] (dissoc results :venues)]
    (when (seq items)
      (println (str "## " (-> category name str/upper-case (str/replace "_" " "))
                    " (" (count items) ")"))
      (doseq [{:keys [title url venue]} (take 10 items)]
        (println " -" title (when venue (str "[" venue "]")))
        (println "  " url))
      (println))))

;; Public API

(defn discover
  "Auto-discover opportunities in a location.
   Usage: (discover \"San Jose, CA\") or (discover \"95112\")"
  [location-str]
  (println "\n=== Nextplace Discovery ===")
  (println "Location:" location-str "\n")

  (print "Geocoding... ")
  (if-let [geo (discovery/geocode location-str)]
    (do
      (println (:formatted_address geo))
      (println "Coordinates:" (:lat geo) "," (:lng geo) "\n")
      (reset! current-location geo)

      (println "Finding venues...")
      (let [venues (discovery/find-venues geo)]
        (println "  Found" (count venues) "venues\n")

        (println "Searching opportunities...")
        (let [results (search-all-categories (:formatted_address geo) venues)]
          (print-results results)
          results)))
    (do
      (println "FAILED")
      (println "Could not geocode location:" location-str)
      nil)))

(defn results
  "Show the last discovery results (if any)"
  []
  (if @current-location
    (println "Last location:" (:formatted_address @current-location))
    (println "No discovery run yet. Use (discover \"location\") to start.")))

;; Suggestion display

(defn- format-suggestion
  "Format a suggestion for display"
  [{:keys [place activity weather time location]}]
  (println "\n╔════════════════════════════════════════════════════════════╗")
  (println "║                    NEXTPLACE SUGGESTION                    ║")
  (println "╚════════════════════════════════════════════════════════════╝\n")
  (println "📍 PLACE")
  (println "  " (:name place))
  (when (:type place)
    (println "   Type:" (:type place)))
  (println)
  (println "🎯 ACTIVITY")
  (println "  " (:activity activity))
  (println "  " (:description activity))
  (println "   Duration:" (:duration activity))
  (println)
  (println "🌤️  WEATHER")
  (println "  " (:temperature weather) "°" (:temperature_unit weather))
  (println "  " (:short_forecast weather))
  (when (:wind_speed weather)
    (println "   Wind:" (:wind_speed weather) (:wind_direction weather)))
  (println)
  (println "🕐 WHEN")
  (println "  " (:formatted time))
  (println)
  (println "📍 MEETING POINT")
  (println "   Main entrance or parking lot")
  (println "   (Precise meeting point TBD)")
  (println)
  (println "─────────────────────────────────────────────────────────────")
  (println "           [ JOIN ]           [ SKIP ]")
  (println "─────────────────────────────────────────────────────────────\n"))

(defn suggest
  "Generate a Flow 1 style suggestion for a location.
   Usage: (suggest) - uses last discovered location
          (suggest \"San Jose, CA\") - uses specified location"
  ([]
   (if @current-location
     (suggest (:input @current-location))
     (println "No location set. Use (suggest \"location\") or (discover \"location\") first.")))
  ([location-str]
   (println "\n=== Generating Suggestion ===")
   (println "Location:" location-str "\n")

   (print "Geocoding... ")
   (if-let [geo (discovery/geocode location-str)]
     (do
       (println (:formatted_address geo))
       (reset! current-location geo)

       (print "Fetching weather... ")
       (if-let [weather (discovery/fetch-weather geo)]
         (do
           (println (:short_forecast weather) "-" (:temperature weather) "°" (:temperature_unit weather))

           (print "Finding venues... ")
           (let [venues (discovery/find-venues geo)]
             (println (count venues) "found")

             (if (seq venues)
               (let [place    (rand-nth (take 20 venues))
                     activity (discovery/match-activity weather place)
                     time     (discovery/generate-event-time)]
                 (format-suggestion {:place    place
                                     :activity activity
                                     :weather  weather
                                     :time     time
                                     :location geo})
                 {:place    place
                  :activity (dissoc activity :match-fn)
                  :weather  weather
                  :time     time
                  :location geo})
               (do
                 (println "\nNo suitable places found nearby.")
                 nil))))
         (do
           (println "FAILED (NWS API error)")
           nil)))
     (do
       (println "FAILED")
       (println "Could not geocode location:" location-str)
       nil))))
