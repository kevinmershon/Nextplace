(ns nextplace.availability
  "Venue availability checking based on OSM opening hours and heuristics.
   Determines if venues are likely open at specific times."
  (:require [clojure.string :as str])
  (:import [java.time DayOfWeek LocalDateTime]))

;; =============================================================================
;; OSM Opening Hours Parsing
;; =============================================================================

(def ^:private day-abbrevs
  "Map OSM day abbreviations to DayOfWeek."
  {"Mo" DayOfWeek/MONDAY
   "Tu" DayOfWeek/TUESDAY
   "We" DayOfWeek/WEDNESDAY
   "Th" DayOfWeek/THURSDAY
   "Fr" DayOfWeek/FRIDAY
   "Sa" DayOfWeek/SATURDAY
   "Su" DayOfWeek/SUNDAY})

(defn- parse-time-range
  "Parse a time range like '09:00-17:00' into [start-minutes end-minutes]"
  [time-str]
  (when-let [[_ h1 m1 h2 m2] (re-matches #"(\d{2}):(\d{2})-(\d{2}):(\d{2})" time-str)]
    [(+ (* (Integer/parseInt h1) 60) (Integer/parseInt m1))
     (+ (* (Integer/parseInt h2) 60) (Integer/parseInt m2))]))

(defn- parse-day-range
  "Parse day range like 'Mo-Fr' or 'Mo' into set of DayOfWeek"
  [day-str]
  (cond
    ;; Range like Mo-Fr
    (str/includes? day-str "-")
    (let [[start end] (str/split day-str #"-")
          start-val   (.getValue (get day-abbrevs start))
          end-val     (.getValue (get day-abbrevs end))]
      (set (map #(DayOfWeek/of %)
                (range start-val (inc end-val)))))

    ;; Single day
    (get day-abbrevs day-str)
    #{(get day-abbrevs day-str)}

    :else nil))

(defn parse-opening-hours
  "Parse OSM opening_hours string into structured data.
   Returns list of {:days #{DayOfWeek} :start minutes :end minutes}"
  [hours-str]
  (when hours-str
    (cond
      ;; 24/7
      (= "24/7" hours-str)
      [{:days  (set (map DayOfWeek/of (range 1 8)))
        :start 0 :end 1440}]

      :else
      ;; Parse semicolon-separated rules like "Mo-Fr 09:00-17:00; Sa 10:00-14:00"
      (->> (str/split hours-str #";\s*")
           (keep (fn [rule]
                   (when-let [[_ days times] (re-matches #"([A-Za-z,-]+)\s+(\d{2}:\d{2}-\d{2}:\d{2})" rule)]
                     (when-let [[start end] (parse-time-range times)]
                       (when-let [day-set (parse-day-range days)]
                         {:days day-set :start start :end end})))))
           seq))))

(defn check-opening-hours
  "Check if venue is open at given time based on opening_hours string.
   Returns :open, :closed, or :unknown"
  [hours-str ^LocalDateTime dt]
  (if-let [rules (parse-opening-hours hours-str)]
    (let [target-day    (.getDayOfWeek dt)
          target-mins   (+ (* (.getHour dt) 60) (.getMinute dt))
          matching-rule (first (filter #(contains? (:days %) target-day) rules))]
      (if matching-rule
        (if (and (>= target-mins (:start matching-rule))
                 (< target-mins (:end matching-rule)))
          :open
          :closed)
        :closed))
    :unknown))

;; =============================================================================
;; Venue Type Classification
;; =============================================================================

(def outdoor-venue-types
  "Venue types that are primarily outdoors (always accessible)."
  #{"park" "path" "footway" "garden" "nature_reserve"
    "dog_park" "beach" "viewpoint" "square" "plaza"
    "playground" "recreation_ground"})

(def typically-closed-sunday
  "Venue types that are typically closed on Sundays."
  #{"library" "museum"})

;; =============================================================================
;; Venue Availability Checking
;; =============================================================================

(defn venue-likely-open?
  "Check if venue is likely open at the given time.
   Uses OSM opening_hours if available, otherwise conservative heuristics."
  [venue ^LocalDateTime dt]
  (let [venue-type (:type venue)
        hours-str  (:opening_hours venue)
        hour       (.getHour dt)
        day        (.getDayOfWeek dt)
        is-sunday  (= day DayOfWeek/SUNDAY)
        is-weekend (or is-sunday (= day DayOfWeek/SATURDAY))]

    ;; First check OSM opening_hours if available
    (case (check-opening-hours hours-str dt)
      :open true
      :closed false
      ;; :unknown - fall back to conservative heuristics
      (cond
        ;; Outdoor venues: 6am-10pm
        (outdoor-venue-types venue-type)
        (and (>= hour 6) (< hour 22))

        ;; Libraries typically closed Sundays, limited Saturday hours
        (and (= venue-type "library") is-sunday)
        false

        ;; Libraries typically close by 5pm (conservative)
        (and (= venue-type "library") (>= hour 17))
        false

        ;; Museums closed Sundays/Mondays typically
        (and (= venue-type "museum")
             (or is-sunday (= day DayOfWeek/MONDAY)))
        false

        ;; Museums typically close by 5pm
        (and (= venue-type "museum") (>= hour 17))
        false

        ;; Cafes typically open 6am-8pm (conservative)
        (= venue-type "cafe")
        (and (>= hour 6) (< hour 20))

        ;; Default: assume open during business hours (9am-5pm weekdays)
        :else
        (and (>= hour 9) (< hour 17) (not is-weekend))))))

(defn filter-likely-open
  "Filter venues to those likely open at the given time.
   Returns original list if none are open (fallback for outdoor activities)."
  [venues ^LocalDateTime dt]
  (let [open-venues (filter #(venue-likely-open? % dt) venues)]
    (if (seq open-venues)
      open-venues
      venues)))
