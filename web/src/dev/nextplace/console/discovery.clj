(ns nextplace.console.discovery
  "Discovery console for REPL usage.
   Thin wrapper around core nextplace.discovery functions."
  (:require [integrant.repl.state :as state]
            [nextplace.console.util :as util]
            [nextplace.discovery :as discovery]
            [nextplace.search :as search]
            [nextplace.time :as time]))

;; =============================================================================
;; Console State
;; =============================================================================

(defonce ^:private back-ns (atom 'user))
(defonce ^:private current-location (atom nil))

(defn- get-db
  "Get the db from the running system, or nil if not started."
  []
  (get state/system :nextplace/db))

;; =============================================================================
;; Navigation Commands
;; =============================================================================

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

;; =============================================================================
;; Display Formatters
;; =============================================================================

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
      (println (str "## " (-> category name clojure.string/upper-case (clojure.string/replace "_" " "))
                    " (" (count items) ")"))
      (doseq [{:keys [title url venue]} (take 10 items)]
        (println " -" title (when venue (str "[" venue "]")))
        (println "  " url))
      (println))))

(defn- format-suggestion
  "Format a suggestion for console display."
  [{:keys [place activity weather time source]}]
  (println "\n+------------------------------------------------------------+")
  (println (if (= source :scheduled)
             "|                  VERIFIED SCHEDULED EVENT                   |"
             "|                    NEXTPLACE SUGGESTION                     |"))
  (println "+------------------------------------------------------------+\n")
  (println "PLACE")
  (println "  " (:name place))
  (when (:type place)
    (println "   Type:" (:type place)))
  (when (:website place)
    (println "   Website:" (:website place)))
  (println)
  (println "ACTIVITY")
  (println "  " (:activity activity))
  (when (:description activity)
    (println "  " (:description activity)))
  (when (:duration activity)
    (println "   Duration:" (:duration activity)))
  (println)
  (println "WEATHER")
  (println "  " (:temperature weather) "deg" (:temperature_unit weather))
  (println "  " (:short_forecast weather))
  (when (:wind_speed weather)
    (println "   Wind:" (:wind_speed weather) (:wind_direction weather)))
  (println)
  (println "WHEN")
  (println "  " (:formatted time))
  (println)
  (when (= source :scheduled)
    (println "VERIFIED via web search")
    (println))
  (println "-------------------------------------------------------------")
  (println "           [ JOIN ]           [ SKIP ]")
  (println "-------------------------------------------------------------\n"))

;; =============================================================================
;; Public API (5 functions only)
;; =============================================================================

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
      (let [venues (discovery/find-venues (get-db) geo)]
        (println "  Found" (count venues) "venues\n")

        (println "Searching opportunities...")
        (let [results (search/search-opportunities (:formatted_address geo) venues)]
          (print-results results)
          results)))
    (do
      (println "FAILED")
      (println "Could not geocode location:" location-str)
      nil)))

(defn suggest
  "Generate THE suggestion for a location.

   This is a thin console wrapper around the core discovery/generate-suggestion.
   In production, the app calls the core function directly with GPS coordinates.

   Usage: (suggest) - uses last discovered location
          (suggest \"San Jose, CA\") - geocode and suggest
          (suggest \"San Jose, CA\" :evening) - simulate time for validation
          (suggest \"San Jose, CA\" {:day :monday :time :after-work})

   Time presets (for validation only):
     :morning (9am), :lunch (12pm), :afternoon (2pm),
     :after-work (5:30pm), :evening (7pm), :night (9pm)
   Days: :monday :tuesday :wednesday :thursday :friday :saturday :sunday"
  ([]
   (if @current-location
     (suggest (:input @current-location) nil)
     (println "No location set. Use (suggest \"location\") first.")))
  ([location-str]
   (suggest location-str nil))
  ([location-str time-spec]
   (println "\n=== Generating Suggestion ===")
   (println "Location:" location-str)
   (when time-spec
     (println "Simulated time:" (if (keyword? time-spec)
                                  (name time-spec)
                                  (str time-spec))))
   (println)

   (print "Geocoding... ")
   (if-let [geo (discovery/geocode location-str)]
     (do
       (println (:formatted_address geo))
       (reset! current-location geo)

       ;; Parse time spec to LocalDateTime for validation
       (let [reference-time (when time-spec (time/parse-time-spec time-spec geo))]
         (println "Generating suggestion...")
         (println)

         ;; Call the core function
         (if-let [suggestion (discovery/generate-suggestion geo reference-time)]
           (do
             (format-suggestion suggestion)
             suggestion)
           (do
             (println "No suggestion could be generated.")
             (println "Check weather API and venue data.")
             nil))))
     (do
       (println "FAILED")
       (println "Could not geocode location:" location-str)
       nil))))
