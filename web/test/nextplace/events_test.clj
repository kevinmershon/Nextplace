(ns nextplace.events-test
  "Tests for nextplace.events - event verification with heuristic analysis."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [nextplace.events :as events])
  (:import [java.time LocalDate DayOfWeek]))

;; =============================================================================
;; Test Data
;; =============================================================================

(def red-rock-coffee
  {:name    "Red Rock Coffee"
   :osm_id  "test-red-rock"
   :type    "cafe"
   :website "https://redrockcoffee.org"})

(def open-mic-template
  {:event-type   "open-mic"
   :activity     "Open mic night"
   :description  "Watch or perform music, comedy, or poetry"
   :duration     "2-3 hours"
   :mood         :social
   :search-terms ["open mic" "open mike" "open stage"]
   :venue-types  #{"cafe" "brewery" "bar" "community_centre"}})

(def karaoke-template
  {:event-type   "karaoke"
   :activity     "Karaoke night"
   :description  "Sing your heart out with friends"
   :duration     "2-3 hours"
   :mood         :social
   :search-terms ["karaoke" "karaoke night" "sing along"]
   :venue-types  #{"bar" "restaurant" "brewery"}})

(def seven-stars-bar
  {:name    "7 Stars Bar and Grill"
   :osm_id  "test-7-stars"
   :type    "bar"
   :website nil})

;; =============================================================================
;; Helper
;; =============================================================================

(defn next-day-of-week
  "Find the next occurrence of a day of week from today."
  [^DayOfWeek dow]
  (let [today      (LocalDate/now)
        today-dow  (.getDayOfWeek today)
        days-ahead (mod (- (.getValue dow) (.getValue today-dow)) 7)
        days-ahead (if (zero? days-ahead) 7 days-ahead)]
    (.plusDays today days-ahead)))

;; =============================================================================
;; Pure Function Tests - parse-time-string
;; =============================================================================

(deftest parse-time-string-test
  (testing "parses simple pm time"
    (is (= [18 0] (#'events/parse-time-string "6pm"))))

  (testing "parses time with minutes and PM"
    (is (= [19 30] (#'events/parse-time-string "7:30 PM"))))

  (testing "parses am time"
    (is (= [9 0] (#'events/parse-time-string "9am"))))

  (testing "handles noon correctly"
    (is (= [12 0] (#'events/parse-time-string "12pm"))))

  (testing "handles midnight correctly"
    (is (= [0 0] (#'events/parse-time-string "12am"))))

  (testing "parses time with period notation"
    (is (= [19 0] (#'events/parse-time-string "7:00 p.m."))))

  (testing "returns nil when no time pattern found"
    (is (nil? (#'events/parse-time-string "No time here"))))

  (testing "returns nil for nil input"
    (is (nil? (#'events/parse-time-string nil)))))

;; =============================================================================
;; Heuristic Verification Tests
;; =============================================================================

(def sample-html-strong-match
  "Red Rock Coffee hosts Open mic night every Monday at 6pm.
   Join us for music, poetry, and comedy. Open mic starts at 6:00 PM.
   Red Rock Coffee Mountain View - your local open mic venue.")

(def sample-html-weak-match
  "Red Rock Coffee is a great cafe in Mountain View.
   They have coffee and pastries. Open daily from 7am to 9pm.")

(def sample-html-wrong-day
  "Red Rock Coffee hosts Open mic night every Tuesday at 7pm.
   Tuesday open mic is our most popular event!")

(deftest verify-event-heuristic-test
  (testing "strong match - venue + event + day all connected"
    (let [monday (next-day-of-week DayOfWeek/MONDAY)
          result (#'events/verify-event-heuristic
                  sample-html-strong-match "Red Rock Coffee" open-mic-template monday)]
      (is (:verified result) "Should verify when all signals align")
      (is (>= (:confidence result) 0.5) "Confidence should be >= 0.5")
      (is (str/includes? (:reasoning result) "Recurring pattern")
          "Should detect recurring pattern")))

  (testing "weak match - venue found but no event connection"
    (let [monday (next-day-of-week DayOfWeek/MONDAY)
          result (#'events/verify-event-heuristic
                  sample-html-weak-match "Red Rock Coffee" open-mic-template monday)]
      (is (not (:verified result)) "Should not verify without event connection")
      (is (< (:confidence result) 0.5) "Confidence should be low")))

  (testing "wrong day - event exists but on different day"
    (let [monday (next-day-of-week DayOfWeek/MONDAY)
          result (#'events/verify-event-heuristic
                  sample-html-wrong-day "Red Rock Coffee" open-mic-template monday)]
      ;; Venue + event terms found, but day doesn't match
      (is (< (:confidence result) 0.5)
          "Confidence should be low when target day not mentioned"))))

(deftest extract-text-snippets-test
  (testing "removes HTML tags"
    (let [html   "<div>Hello <b>World</b></div>"
          result (#'events/extract-text-snippets html)]
      (is (str/includes? result "Hello"))
      (is (str/includes? result "World"))
      (is (not (str/includes? result "<div>")))))

  (testing "removes script tags"
    (let [html   "<script>alert('bad')</script><p>Good</p>"
          result (#'events/extract-text-snippets html)]
      (is (str/includes? result "Good"))
      (is (not (str/includes? result "alert"))))))

(deftest score-venue-event-connection-test
  (testing "high score when venue and event terms are close"
    (let [text  "Red Rock Coffee open mic night every Monday"
          score (#'events/score-venue-event-connection
                 text "Red Rock Coffee" ["open mic"])]
      (is (> score 0.5) "Should score high when terms are nearby")))

  (testing "low score when venue found but event terms missing"
    (let [text  "Red Rock Coffee is a great place for coffee"
          score (#'events/score-venue-event-connection
                 text "Red Rock Coffee" ["open mic"])]
      (is (<= score 0.2) "Should score low without event terms")))

  (testing "zero score when venue not found"
    (let [text  "Some other cafe has open mic nights"
          score (#'events/score-venue-event-connection
                 text "Red Rock Coffee" ["open mic"])]
      (is (= 0.0 score) "Should be zero when venue not mentioned"))))

(deftest score-day-event-connection-test
  (testing "high score with recurring pattern"
    (let [text  "Open mic every Monday at 6pm"
          score (#'events/score-day-event-connection
                 text :monday ["open mic"])]
      (is (> score 0.5) "Should score high with recurring + day + event")))

  (testing "moderate score without recurring pattern"
    (let [text  "Open mic on Monday"
          score (#'events/score-day-event-connection
                 text :monday ["open mic"])]
      (is (> score 0.3) "Should have some score with day + event")))

  (testing "zero score when day not mentioned"
    (let [text  "Open mic tonight at 6pm"
          score (#'events/score-day-event-connection
                 text :monday ["open mic"])]
      (is (= 0.0 score) "Should be zero when target day not mentioned"))))

;; =============================================================================
;; Live Integration Tests - DuckDuckGo
;; =============================================================================

(deftest ^:live fetch-search-results-test
  (testing "fetches results from DuckDuckGo"
    (let [result (#'events/fetch-search-results "Red Rock Coffee Mountain View open mic")]
      (is (some? result) "Should return HTML from DuckDuckGo")
      (when result
        (is (string? result))
        (is (pos? (count result)))))))

(deftest ^:live red-rock-coffee-open-mic-litmus
  (testing "LITMUS: Red Rock Coffee open mic Monday"
    (let [next-monday (next-day-of-week DayOfWeek/MONDAY)
          query       (#'events/search-query "Red Rock Coffee" open-mic-template)
          _           (println "\n=== LITMUS TEST: Red Rock Coffee Open Mic ===")
          _           (println "Target date:" next-monday "(Monday)")
          _           (println "Search query:" query)
          search-html (#'events/fetch-search-results query)
          _           (println "Search returned:" (count (or search-html "")) "chars")]

      (is (some? search-html) "Should get search results")

      (when search-html
        (let [result (#'events/verify-event-heuristic
                      search-html "Red Rock Coffee" open-mic-template next-monday)]
          (println "Verification result:" result)
          (println "Verified:" (:verified result))
          (println "Confidence:" (format "%.2f" (:confidence result)))
          (println "Event time:" (:event_time result))
          (println "Reasoning:" (:reasoning result))
          (is (:verified result) "Red Rock Coffee should have open mic on Monday"))))))

(deftest ^:live seven-stars-karaoke-litmus
  (testing "LITMUS: 7 Stars Bar and Grill karaoke Friday"
    (let [next-friday (next-day-of-week DayOfWeek/FRIDAY)
          query       (#'events/search-query "7 Stars Bar and Grill" karaoke-template)
          _           (println "\n=== LITMUS TEST: 7 Stars Karaoke ===")
          _           (println "Target date:" next-friday "(Friday)")
          _           (println "Search query:" query)
          search-html (#'events/fetch-search-results query)
          _           (println "Search returned:" (count (or search-html "")) "chars")]

      (is (some? search-html) "Should get search results")

      (when search-html
        (let [result (#'events/verify-event-heuristic
                      search-html "7 Stars Bar and Grill" karaoke-template next-friday)]
          (println "Verification result:" result)
          (println "Verified:" (:verified result))
          (println "Confidence:" (format "%.2f" (:confidence result)))
          (println "Event time:" (:event_time result))
          (println "Reasoning:" (:reasoning result))
          (is (:verified result) "7 Stars should have karaoke on Friday"))))))

;; =============================================================================
;; Area Search Tests (Pure Functions)
;; =============================================================================

;; Longer sample with clear separation between venues (>500 chars between sections)
(def sample-area-html
  (str "Mountain View open mic events. Red Rock Coffee hosts open mic every Monday at 6pm. "
       "The weekly open mic at Red Rock Coffee is popular with local musicians and poets. "
       "Sign-ups start at 5:30pm and performances begin promptly at 6pm. "
       (apply str (repeat 100 "x"))  ;; Padding to separate sections
       " Dana Street Roasting Company is located downtown and serves excellent pour-over coffee. "
       "They host trivia nights on Wednesdays which draw a competitive crowd. "
       "The trivia starts at 7pm and teams of up to 6 are welcome. "
       (apply str (repeat 100 "x"))  ;; More padding
       " Blue Bottle Coffee serves premium single-origin beans. "
       "Their minimalist design and careful brewing makes for a peaceful experience. "
       "No events are hosted here, just great coffee in a quiet atmosphere."))

(def mountain-view-venues
  [{:name "Red Rock Coffee" :osm_id "test-1" :type "cafe"}
   {:name "Dana Street Roasting" :osm_id "test-2" :type "cafe"}
   {:name "Blue Bottle Coffee" :osm_id "test-3" :type "cafe"}
   {:name "Some Random Park" :osm_id "test-4" :type "park"}])

(deftest extract-venue-mentions-test
  (testing "finds venues mentioned with event terms"
    (let [result (#'events/extract-venue-mentions sample-area-html ["open mic"] mountain-view-venues)]
      (is (= 1 (count result)) "Should find Red Rock mentioned with open mic")
      (is (= "Red Rock Coffee" (:name (first result))))))

  (testing "returns empty for unrelated venues"
    (let [result (#'events/extract-venue-mentions sample-area-html ["karaoke"] mountain-view-venues)]
      (is (empty? result) "No venue mentioned with karaoke")))

  (testing "finds trivia venue correctly"
    (let [result (#'events/extract-venue-mentions sample-area-html ["trivia"] mountain-view-venues)]
      (is (= 1 (count result)) "Should find Dana Street with trivia")
      (is (= "Dana Street Roasting" (:name (first result))))))

  (testing "does not match venue without event term proximity"
    (let [result (#'events/extract-venue-mentions sample-area-html ["open mic"] mountain-view-venues)]
      (is (not (some #(= "Blue Bottle Coffee" (:name %)) result))
          "Blue Bottle not mentioned with open mic"))))
