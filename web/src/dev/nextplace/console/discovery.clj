(ns nextplace.console.discovery
  "Discovery console for auto-discovering opportunities by location"
  (:require [clj-http.client :as http]
            [clojure.data.json :as json]
            [clojure.pprint :refer [pprint]]
            [clojure.string :as str]
            [nextplace.console.util :as util])
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

;; Geocoding via OpenStreetMap Nominatim (free, no API key)

(defn- geocode-location
  "Geocode a location string to coordinates using OSM Nominatim.
   Biased to USA results for MVP (South SF Bay Area focus)."
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

;; Overpass API for parks

(defn- overpass-query
  "Execute an Overpass API query"
  [query]
  (let [url      "https://overpass-api.de/api/interpreter"
        response (http/post url (merge http-opts
                                       {:form-params {:data query}}))]
    (when (= 200 (:status response))
      (json/read-str (:body response) :key-fn keyword))))

(defn- find-parks
  "Find parks near coordinates"
  [{:keys [lat lng bounds]}]
  (let [radius (if bounds
                 (let [ne-lat (get-in bounds [:northeast :lat])
                       sw-lat (get-in bounds [:southwest :lat])]
                   (max 5000 (* 111000 (- ne-lat sw-lat))))
                 10000)
        query  (str "[out:json][timeout:30];"
                    "(way[\"leisure\"=\"park\"](around:" radius "," lat "," lng ");"
                    " relation[\"leisure\"=\"park\"](around:" radius "," lat "," lng ");"
                    " way[\"leisure\"=\"nature_reserve\"](around:" radius "," lat "," lng ");"
                    " way[\"landuse\"=\"recreation_ground\"](around:" radius "," lat "," lng "););"
                    "out body geom;")]
    (when-let [result (overpass-query query)]
      (->> (:elements result)
           (filter #(get-in % [:tags :name]))
           (map (fn [el]
                  {:name     (get-in el [:tags :name])
                   :type     (or (get-in el [:tags :leisure])
                                 (get-in el [:tags :landuse]))
                   :website  (get-in el [:tags :website])
                   :osm_id   (:id el)
                   :osm_type (:type el)}))
           (distinct)
           (sort-by :name)))))

;; Web Search

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
  [location-name parks]
  (let [categories [["nonprofit volunteer opportunities" :volunteer]
                    ["park cleanup community service" :park_cleanup]
                    ["animal rescue volunteer humane society" :animal_rescue]
                    ["walking group hiking meetup easy" :walking_groups]
                    ["open streets community event festival" :community_events]]
        results    (atom {:parks parks})]

    ;; Base location searches
    (doseq [[suffix category] categories]
      (println "  Searching:" (name category))
      (let [query          (str location-name " " suffix)
            search-results (ddg-search query)]
        (swap! results assoc category search-results)
        (Thread/sleep 1000)))

    ;; Park-specific searches for events
    (when (seq parks)
      (println "  Searching park events...")
      (let [park-events (atom [])]
        (doseq [park (take 5 parks)]
          (let [query   (str (:name park) " " location-name " cleanup meetup event")
                results (ddg-search query)]
            (when (seq results)
              (swap! park-events concat
                     (map #(assoc % :park (:name park)) results)))
            (Thread/sleep 500)))
        (swap! results assoc :park_events (distinct @park-events))))

    @results))

(defn- print-results
  "Print discovery results in readable format"
  [results]
  (println "\n=== Discovery Results ===\n")

  (when-let [parks (:parks results)]
    (println "## PARKS & NATURE AREAS (" (count parks) ")")
    (doseq [{:keys [name type website]} parks]
      (println " -" name (str "(" type ")")
               (when website (str "\n   " website))))
    (println))

  (doseq [[category items] (dissoc results :parks)]
    (when (seq items)
      (println (str "## " (-> category name str/upper-case (str/replace "_" " "))
                    " (" (count items) ")"))
      (doseq [{:keys [title url park]} (take 10 items)]
        (println " -" title (when park (str "[" park "]")))
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
  (if-let [geo (geocode-location location-str)]
    (do
      (println (:formatted_address geo))
      (println "Coordinates:" (:lat geo) "," (:lng geo) "\n")
      (reset! current-location geo)

      (println "Finding parks...")
      (let [parks (find-parks geo)]
        (println "  Found" (count parks) "parks/nature areas\n")

        (println "Searching opportunities...")
        (let [results (search-all-categories (:formatted_address geo) parks)]
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
