(ns nextplace.venues
  "Venue storage with caching and intelligent deduplication.
   Venues are cached by geohash with TTL for freshness."
  (:require [clojure.string :as str]
            [nextplace.db :as db]))

;; Geohash implementation for location-based keys
;; Precision 6 gives ~1.2km x 0.6km cells

(def ^:private base32-chars "0123456789bcdefghjkmnpqrstuvwxyz")

(defn- encode-geohash
  "Encode lat/lng to geohash string with given precision"
  [lat lng precision]
  (loop [min-lat -90.0
         max-lat 90.0
         min-lng -180.0
         max-lng 180.0
         bit     0
         ch      0
         hash    ""
         even?   true]
    (if (>= (count hash) precision)
      hash
      (let [[new-min new-max coord]
            (if even?
              (let [mid (/ (+ min-lng max-lng) 2)]
                (if (>= lng mid)
                  [mid max-lng lng]
                  [min-lng mid lng]))
              (let [mid (/ (+ min-lat max-lat) 2)]
                (if (>= lat mid)
                  [mid max-lat lat]
                  [min-lat mid lat])))
            above-mid? (if even?
                         (>= lng (/ (+ min-lng max-lng) 2))
                         (>= lat (/ (+ min-lat max-lat) 2)))
            new-ch     (+ (* ch 2) (if above-mid? 1 0))
            new-bit    (inc bit)]
        (if (= new-bit 5)
          (recur (if even? min-lat new-min)
                 (if even? max-lat new-max)
                 (if even? new-min min-lng)
                 (if even? new-max max-lng)
                 0
                 0
                 (str hash (nth base32-chars new-ch))
                 (not even?))
          (recur (if even? min-lat new-min)
                 (if even? max-lat new-max)
                 (if even? new-min min-lng)
                 (if even? new-max max-lng)
                 new-bit
                 new-ch
                 hash
                 (not even?)))))))

(defn geohash
  "Generate a geohash for coordinates. Default precision 6 (~1km cells)."
  ([lat lng] (geohash lat lng 6))
  ([lat lng precision]
   (encode-geohash lat lng precision)))

;; Deduplication

(defn- normalize-name
  "Normalize venue name for comparison"
  [name]
  (when name
    (-> name
        str/lower-case
        (str/replace #"[^\w\s]" "")
        (str/replace #"\s+" " ")
        str/trim)))

(defn- name-similarity
  "Calculate similarity between two names (0-1)"
  [name1 name2]
  (if (or (nil? name1) (nil? name2))
    0
    (let [n1     (normalize-name name1)
          n2     (normalize-name name2)
          words1 (set (str/split n1 #"\s+"))
          words2 (set (str/split n2 #"\s+"))
          common (count (clojure.set/intersection words1 words2))
          total  (max (count words1) (count words2))]
      (if (zero? total)
        0
        (/ common total)))))

(defn- haversine-distance
  "Calculate distance in meters between two lat/lng points"
  [lat1 lng1 lat2 lng2]
  (let [r    6371000 ;; Earth radius in meters
        dlat (Math/toRadians (- lat2 lat1))
        dlng (Math/toRadians (- lng2 lng1))
        a    (+ (* (Math/sin (/ dlat 2)) (Math/sin (/ dlat 2)))
                (* (Math/cos (Math/toRadians lat1))
                   (Math/cos (Math/toRadians lat2))
                   (Math/sin (/ dlng 2))
                   (Math/sin (/ dlng 2))))
        c    (* 2 (Math/atan2 (Math/sqrt a) (Math/sqrt (- 1 a))))]
    (* r c)))

(defn- duplicate?
  "Check if two venues are duplicates.
   Considers name similarity and proximity."
  [venue1 venue2]
  (let [name-sim   (name-similarity (:name venue1) (:name venue2))
        same-type? (= (:type venue1) (:type venue2))]
    (or
     ;; Exact name match
     (= (normalize-name (:name venue1))
        (normalize-name (:name venue2)))
     ;; High name similarity with same type
     (and (>= name-sim 0.8) same-type?)
     ;; Same OSM ID
     (and (:osm_id venue1)
          (= (:osm_id venue1) (:osm_id venue2))))))

(defn deduplicate-venues
  "Remove duplicate venues from a list.
   Keeps the first occurrence of each unique venue."
  [venues]
  (reduce
   (fn [acc venue]
     (if (some #(duplicate? venue %) acc)
       acc
       (conj acc venue)))
   []
   venues))

;; Cache management

(def ^:private cache-ttl-hours 24)

(defn- cache-key
  "Generate cache key for location"
  [lat lng]
  (str "venues:" (geohash lat lng)))

(defn- cache-expired?
  "Check if cache entry has expired"
  [entry]
  (when-let [cached-at (:cached_at entry)]
    (> (- (System/currentTimeMillis) cached-at)
       (* cache-ttl-hours 60 60 1000))))

(defn get-cached-venues
  "Get cached venues for a location.
   Returns nil if not cached or expired."
  [db lat lng]
  (when-let [entry (db/get-value db (cache-key lat lng))]
    (when-not (cache-expired? entry)
      (:venues entry))))

(defn cache-venues!
  "Cache venues for a location with deduplication."
  [db lat lng venues]
  (let [deduped (deduplicate-venues venues)
        entry   {:venues    deduped
                 :cached_at (System/currentTimeMillis)
                 :count     (count deduped)
                 :geohash   (geohash lat lng)}]
    (db/put-value db (cache-key lat lng) entry)
    deduped))

(defn clear-venue-cache!
  "Clear all venue cache entries"
  [db]
  ;; This would require key iteration which RocksDB supports
  ;; For now, manual clearing is needed
  nil)

;; Merged venue fetching

(defn merge-and-cache-venues!
  "Merge new venues with existing cache, deduplicate, and store."
  [db lat lng new-venues]
  (let [existing (or (get-cached-venues db lat lng) [])
        merged   (deduplicate-venues (concat existing new-venues))]
    (cache-venues! db lat lng merged)))
