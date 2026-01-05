(ns nextplace.time
  "Time utilities for location-aware time handling.
   US-focused timezone lookup and time parsing for suggestions."
  (:import [java.time DayOfWeek Instant LocalDate LocalDateTime ZonedDateTime ZoneId]
           [java.time.format DateTimeFormatter]))

;; =============================================================================
;; Timezone Lookup (US-focused for MVP)
;; =============================================================================

(def ^:private us-timezone-bounds
  "Approximate longitude bounds for US timezones.
   Note: boundaries are approximate, real boundaries follow state/county lines."
  [[-125.0 -115.0 "America/Los_Angeles"]   ;; Pacific
   [-115.0 -102.0 "America/Denver"]        ;; Mountain
   [-102.0 -85.5  "America/Chicago"]       ;; Central
   [-85.5  -65.0  "America/New_York"]])    ;; Eastern (Chattanooga ~-85.3)

(defn timezone-for-coords
  "Get timezone ID for coordinates. US-focused with UTC fallback."
  [lat lng]
  (or (some (fn [[min-lng max-lng tz-id]]
              (when (and (>= lng min-lng) (< lng max-lng)
                         (>= lat 24.0) (<= lat 50.0))  ;; Continental US
                (ZoneId/of tz-id)))
            us-timezone-bounds)
      (ZoneId/of "UTC")))

(defn now-at-location
  "Get current time at a location's timezone."
  [geo]
  (let [tz (timezone-for-coords (:lat geo) (:lng geo))]
    (.toLocalDateTime (ZonedDateTime/now tz))))

;; =============================================================================
;; Day-of-Week and Time Presets
;; =============================================================================

(def day-of-week
  "Map of keyword day names to DayOfWeek values."
  {:monday    DayOfWeek/MONDAY
   :tuesday   DayOfWeek/TUESDAY
   :wednesday DayOfWeek/WEDNESDAY
   :thursday  DayOfWeek/THURSDAY
   :friday    DayOfWeek/FRIDAY
   :saturday  DayOfWeek/SATURDAY
   :sunday    DayOfWeek/SUNDAY})

(def time-presets
  "Named time presets for user-friendly time specification."
  {:morning    {:hour 9 :minute 0}
   :lunch      {:hour 12 :minute 0}
   :afternoon  {:hour 14 :minute 0}
   :after-work {:hour 17 :minute 30}
   :evening    {:hour 19 :minute 0}
   :night      {:hour 21 :minute 0}})

;; =============================================================================
;; Time Calculation Utilities
;; =============================================================================

(def ^:private min-hours-ahead 4)

(defn next-day-of-week
  "Get the next occurrence of a day of week from the location's current date."
  [dow geo]
  (let [now   (.toLocalDate (now-at-location geo))
        today (.getDayOfWeek now)
        days  (mod (- (.getValue dow) (.getValue today)) 7)
        days  (if (zero? days) 7 days)]
    (.plusDays now days)))

(defn- local-to-utc
  "Convert a local datetime at a location to UTC instant."
  [^LocalDateTime dt geo]
  (let [tz (timezone-for-coords (:lat geo) (:lng geo))]
    (.toInstant (.atZone dt tz))))

(defn ensure-future
  "Ensure datetime is at least 4 hours in the future.
   Compares in UTC to avoid timezone issues."
  [^LocalDateTime dt geo]
  (let [dt-utc  (local-to-utc dt geo)
        now-utc (Instant/now)
        min-utc (.plusSeconds now-utc (* min-hours-ahead 3600))]
    (if (.isBefore dt-utc min-utc)
      (recur (.plusDays dt 1) geo)
      dt)))

;; =============================================================================
;; Time Specification Parsing
;; =============================================================================

(defn parse-time-spec
  "Parse a time specification into a LocalDateTime at the given location.
   Always returns a future time (rolls to next day if less than 4 hours away).
   Accepts:
   - {:day :monday :hour 17} or {:day :monday :time :after-work}
   - {:hour 17} for today (or tomorrow if past)
   - :after-work for today at that preset time (or tomorrow if past)"
  [spec geo]
  (let [today (.toLocalDate (now-at-location geo))]
    (cond
      (keyword? spec)
      (let [{:keys [hour minute]} (get time-presets spec {:hour 17 :minute 0})]
        (ensure-future (.atTime today (int hour) (int minute)) geo))

      (map? spec)
      (let [day-kw  (:day spec)
            date    (if day-kw
                      (next-day-of-week (get day-of-week day-kw) geo)
                      today)
            time-kw (:time spec)
            hour    (or (:hour spec)
                        (:hour (get time-presets time-kw))
                        17)
            minute  (or (:minute spec)
                        (:minute (get time-presets time-kw))
                        0)
            dt      (.atTime date (int hour) (int minute))]
        ;; Always ensure future - even specific days should be 4+ hours out
        (ensure-future dt geo))

      :else
      (now-at-location geo))))

(defn format-event-time
  "Format a LocalDateTime for display."
  [^LocalDateTime dt]
  {:start     dt
   :formatted (.format dt (DateTimeFormatter/ofPattern "EEEE h:mm a"))})
