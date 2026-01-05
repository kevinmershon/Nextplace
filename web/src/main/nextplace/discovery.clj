(ns nextplace.discovery
  "Discovery engine for auto-discovering places and activities by location.
   Core business logic for Flows 1 and 2."
  (:require [clj-http.client :as http]
            [clojure.data.json :as json]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [nextplace.db :as db]
            [nextplace.venues :as venues])
  (:import [java.net URLEncoder]))

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
                    {:name       (get tags :name)
                     :type       (or (get tags :leisure)
                                     (get tags :amenity)
                                     (get tags :tourism)
                                     (get tags :natural)
                                     (get tags :landuse)
                                     (get tags :highway)
                                     (get tags :place)
                                     "venue")
                     :cuisine    (get tags :cuisine)
                     :website    (get tags :website)
                     :dog        (= "yes" (get tags :dog))
                     :wheelchair (get tags :wheelchair)
                     :osm_id     (:id el)
                     :osm_type   (:type el)})))
           (distinct)
           (sort-by :name)))))

(defn find-venues
  "Find all venue types near coordinates for activity suggestions.
   Uses caching with intelligent deduplication.
   Returns a list of {:name :type :cuisine :website :dog :wheelchair :osm_id :osm_type}."
  ([geo] (find-venues geo nil))
  ([{:keys [lat lng bounds]} db-conn]
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

;; Venue-specific activities - keyed by venue type
(def venue-activities
  "Activities specific to venue types"
  {"cafe"             [{:activity    "Read a book"
                        :description "Cozy up with a good book and a warm drink"
                        :duration    "1-2 hours"}
                       {:activity    "Work session"
                        :description "Productive solo work in a cafe atmosphere"
                        :duration    "2-3 hours"}
                       {:activity    "People watching"
                        :description "Relax and observe the world go by"
                        :duration    "30-60 minutes"}]
   "library"          [{:activity    "Browse and read"
                        :description "Explore the stacks and find something new"
                        :duration    "1-2 hours"}
                       {:activity    "Quiet study"
                        :description "Focused reading or research time"
                        :duration    "2-3 hours"}]
   "museum"           [{:activity    "Explore exhibits"
                        :description "Discover art, history, or science"
                        :duration    "1-3 hours"}
                       {:activity    "Guided tour"
                        :description "Learn from expert docents"
                        :duration    "1-2 hours"}]
   "park"             [{:activity    "Picnic"
                        :description "Enjoy food outdoors in a beautiful setting"
                        :duration    "1-2 hours"}
                       {:activity    "Chess in the park"
                        :description "Find a table and play a game"
                        :duration    "1-2 hours"}
                       {:activity    "Frisbee"
                        :description "Toss a disc on the lawn"
                        :duration    "30-60 minutes"}
                       {:activity    "Bird watching"
                        :description "Observe local wildlife"
                        :duration    "1-2 hours"}]
   "dog_park"         [{:activity    "Dog socialization"
                        :description "Let your pup make friends"
                        :duration    "30-60 minutes"}]
   "garden"           [{:activity    "Garden stroll"
                        :description "Admire the plants and flowers"
                        :duration    "30-60 minutes"}
                       {:activity    "Sketch the scenery"
                        :description "Capture the beauty in drawings"
                        :duration    "1-2 hours"}]
   "beach"            [{:activity    "Beach walk"
                        :description "Stroll along the shoreline"
                        :duration    "30-60 minutes"}
                       {:activity    "Beach reading"
                        :description "Read with the sound of waves"
                        :duration    "1-2 hours"}
                       {:activity    "Shell collecting"
                        :description "Hunt for interesting shells"
                        :duration    "30-60 minutes"}]
   "viewpoint"        [{:activity    "Sunrise/sunset viewing"
                        :description "Watch the sky transform"
                        :duration    "30-60 minutes"}
                       {:activity    "Landscape photography"
                        :description "Capture the panorama"
                        :duration    "30-60 minutes"}]
   "community_centre" [{:activity    "Drop-in class"
                        :description "Try a community class or workshop"
                        :duration    "1-2 hours"}
                       {:activity    "Game night"
                        :description "Join community board games"
                        :duration    "2-3 hours"}]
   "sports_centre"    [{:activity    "Open gym"
                        :description "Work out at your own pace"
                        :duration    "1-2 hours"}
                       {:activity    "Pick-up game"
                        :description "Join an informal sports game"
                        :duration    "1-2 hours"}]
   "path"             [{:activity    "Trail walk"
                        :description "Enjoy the path at a leisurely pace"
                        :duration    "30-90 minutes"}
                       {:activity    "Trail run"
                        :description "Get some exercise on the trail"
                        :duration    "30-60 minutes"}]
   "footway"          [{:activity    "Urban exploration"
                        :description "Discover hidden corners of the neighborhood"
                        :duration    "1-2 hours"}]
   "square"           [{:activity    "Street performer watching"
                        :description "Enjoy live music and performances"
                        :duration    "30-60 minutes"}
                       {:activity    "Fountain sitting"
                        :description "Relax near the water"
                        :duration    "30-60 minutes"}]})

;; Weather-based outdoor activities (for parks and outdoor venues)
(def weather-activities
  "Activities matched by weather conditions.
   Order matters - first match wins. More specific conditions first."
  [{:activity    "Kite flying"
    :description "Perfect wind for kites"
    :duration    "1-2 hours"
    :match-fn    (fn [w _] (and (str/includes? (safe-wind w) "1")
                                (comfortable-temp? w 55 85)))}
   {:activity    "Photography walk"
    :description "Great light for outdoor photography"
    :duration    "1-2 hours"
    :match-fn    (fn [w _] (or (str/includes? (safe-forecast w) "cloud")
                               (str/includes? (safe-forecast w) "fog")
                               (not (:is_daytime w))))}
   {:activity    "Outdoor yoga"
    :description "Calm, comfortable conditions for outdoor yoga"
    :duration    "1 hour"
    :match-fn    (fn [w _] (and (not (str/includes? (safe-wind w) "1"))
                                (comfortable-temp? w 60 80)
                                (no-rain? w)))}
   {:activity    "Trail walking"
    :description "Clear conditions for an easy trail walk"
    :duration    "1-2 hours"
    :match-fn    (fn [w _] (and (no-rain? w)
                                (comfortable-temp? w 50 85)))}
   {:activity    "Live outdoor music"
    :description "Catch some live music in the open air"
    :duration    "1-3 hours"
    :match-fn    (fn [w _] (and (comfortable-temp? w 60 80)
                                (no-rain? w)
                                (:is_daytime w)))}
   {:activity    "Park cleanup"
    :description "Good weather for community service"
    :duration    "2-3 hours"
    :match-fn    (fn [w _] (and (no-rain? w)
                                (comfortable-temp? w 45 90)))}
   {:activity    "Scenic walk"
    :description "Enjoy the outdoors with a casual stroll"
    :duration    "30-60 minutes"
    :match-fn    (fn [_ _] true)}]) ;; Always valid fallback

(def indoor-venue-types
  "Venue types that are primarily indoors"
  #{"cafe" "library" "museum" "community_centre" "sports_centre"})

(defn match-activity
  "Find the best activity for weather conditions and venue type.
   Considers venue-specific activities and weather conditions."
  ([weather]
   (match-activity weather nil))
  ([weather venue]
   (let [venue-type (:type venue)]
     (cond
       ;; Indoor venues - pick from venue-specific activities (weather doesn't matter much)
       (and venue-type (indoor-venue-types venue-type))
       (let [activities (get venue-activities venue-type)]
         (if (seq activities)
           (rand-nth activities)
           {:activity    "Explore"
            :description (str "Check out " (:name venue))
            :duration    "1-2 hours"}))

       ;; Outdoor venue with venue-specific activities - blend with weather
       (and venue-type (get venue-activities venue-type))
       (let [venue-acts   (get venue-activities venue-type)
             weather-acts (filter #((:match-fn %) weather venue) weather-activities)]
         ;; 50/50 chance of venue-specific vs weather-based activity
         (if (and (seq venue-acts) (< (rand) 0.5))
           (rand-nth venue-acts)
           (or (first weather-acts)
               (last weather-activities))))

       ;; Pure weather-based matching for outdoor venues
       :else
       (or (first (filter #((:match-fn %) weather venue) weather-activities))
           (last weather-activities))))))

;; Suggestion generation

(defn generate-event-time
  "Generate a suggested event time (4-48 hours from now).
   Returns {:start :formatted}."
  []
  (let [now        (java.time.LocalDateTime/now)
        ;; Add 4-8 hours for same-day, or next morning
        hours-add  (if (< (.getHour now) 14)
                     (+ 4 (rand-int 4))   ;; Same day afternoon
                     (+ 16 (rand-int 4))) ;; Tomorrow morning
        start-time (.plusHours now hours-add)
        ;; Round to nearest 30 min
        minute     (.getMinute start-time)
        rounded    (if (< minute 30)
                     (.withMinute start-time 0)
                     (.withMinute start-time 30))]
    {:start     rounded
     :formatted (str (.format rounded (java.time.format.DateTimeFormatter/ofPattern "EEEE h:mm a")))}))

(defn generate-suggestion
  "Generate a Flow 1 style suggestion for a geocoded location.
   Returns {:place :activity :weather :time :location} or nil on failure."
  [geo]
  (when-let [weather (fetch-weather geo)]
    (when-let [venues (seq (find-venues geo))]
      (let [place    (rand-nth (take 20 venues))
            activity (match-activity weather place)
            time     (generate-event-time)]
        {:place    place
         :activity (dissoc activity :match-fn)
         :weather  weather
         :time     time
         :location geo}))))
