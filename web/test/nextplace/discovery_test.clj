(ns nextplace.discovery-test
  "Unit tests for nextplace.discovery"
  (:require [clojure.test :refer [deftest is testing]]
            [clj-http.client :as http]
            [nextplace.discovery :as discovery]))

;; =============================================================================
;; Pure Function Tests (no mocking required)
;; =============================================================================

(deftest match-activity-test
  (testing "matches kite flying for windy warm weather (no venue)"
    (let [weather {:wind_speed "15 mph" :temperature 60 :short_forecast "Sunny" :is_daytime true}
          result  (discovery/match-activity weather)]
      (is (= "Kite flying" (:activity result)))))

  (testing "matches photography walk for cloudy weather (no venue)"
    (let [weather {:wind_speed "5 mph" :temperature 65 :short_forecast "Cloudy" :is_daytime true}
          result  (discovery/match-activity weather)]
      (is (= "Photography walk" (:activity result)))))

  (testing "matches photography walk for night time (no venue)"
    (let [weather {:wind_speed "5 mph" :temperature 65 :short_forecast "Clear" :is_daytime false}
          result  (discovery/match-activity weather)]
      (is (= "Photography walk" (:activity result)))))

  (testing "matches outdoor yoga for calm comfortable weather (no venue)"
    (let [weather {:wind_speed "3 mph" :temperature 70 :short_forecast "Sunny" :is_daytime true}
          result  (discovery/match-activity weather)]
      (is (= "Outdoor yoga" (:activity result)))))

  (testing "matches trail walking for clear moderate weather (no venue)"
    (let [weather {:wind_speed "5 mph" :temperature 55 :short_forecast "Sunny" :is_daytime true}
          result  (discovery/match-activity weather)]
      (is (= "Trail walking" (:activity result)))))

  (testing "matches park cleanup for good weather outside trail walking range (no venue)"
    (let [weather {:wind_speed "5 mph" :temperature 48 :short_forecast "Sunny" :is_daytime true}
          result  (discovery/match-activity weather)]
      (is (= "Park cleanup" (:activity result)))))

  (testing "falls back to scenic walk for rainy weather (no venue)"
    (let [weather {:wind_speed "5 mph" :temperature 55 :short_forecast "Rain" :is_daytime true}
          result  (discovery/match-activity weather)]
      (is (= "Scenic walk" (:activity result)))))

  (testing "falls back to scenic walk for extreme cold (no venue)"
    (let [weather {:wind_speed "5 mph" :temperature 30 :short_forecast "Sunny" :is_daytime true}
          result  (discovery/match-activity weather)]
      (is (= "Scenic walk" (:activity result)))))

  (testing "handles nil weather values gracefully"
    (let [weather {}
          result  (discovery/match-activity weather)]
      (is (some? (:activity result)))))

  ;; Venue-aware tests
  (testing "indoor venue returns venue-specific activity regardless of weather"
    (let [weather {:wind_speed "5 mph" :temperature 55 :short_forecast "Sunny" :is_daytime true}
          venue   {:type "cafe" :name "Test Cafe"}
          result  (discovery/match-activity weather venue)]
      (is (contains? #{"Read a book" "Work session" "People watching"} (:activity result)))))

  (testing "library returns library-specific activity"
    (let [weather {:wind_speed "5 mph" :temperature 55 :short_forecast "Rain" :is_daytime true}
          venue   {:type "library" :name "City Library"}
          result  (discovery/match-activity weather venue)]
      (is (contains? #{"Browse and read" "Quiet study"} (:activity result)))))

  (testing "museum returns museum-specific activity"
    (let [weather {:wind_speed "5 mph" :temperature 30 :short_forecast "Snow" :is_daytime true}
          venue   {:type "museum" :name "Art Museum"}
          result  (discovery/match-activity weather venue)]
      (is (contains? #{"Explore exhibits" "Guided tour"} (:activity result))))))

(deftest generate-event-time-test
  (testing "generates a time map with required keys"
    (let [result (discovery/generate-event-time)]
      (is (contains? result :start))
      (is (contains? result :formatted))
      (is (instance? java.time.LocalDateTime (:start result)))
      (is (string? (:formatted result)))))

  (testing "time is at least 4 hours in the future"
    (let [now    (java.time.LocalDateTime/now)
          result (discovery/generate-event-time)
          hours  (.until now (:start result) java.time.temporal.ChronoUnit/HOURS)]
      (is (>= hours 4))))

  (testing "minutes are rounded to 0 or 30"
    (let [result (discovery/generate-event-time)
          minute (.getMinute (:start result))]
      (is (or (= 0 minute) (= 30 minute))))))

;; =============================================================================
;; HTTP Function Tests (with mocking)
;; =============================================================================

(def mock-nominatim-response
  {:status 200
   :body   "[{\"lat\":\"37.3382\",\"lon\":\"-121.8863\",\"display_name\":\"San Jose, Santa Clara County, California, USA\",\"boundingbox\":[\"37.1\",\"37.5\",\"-122.0\",\"-121.7\"]}]"})

(def mock-nominatim-empty-response
  {:status 200
   :body   "[]"})

(def mock-nominatim-error-response
  {:status 500
   :body   "Internal Server Error"})

(deftest geocode-test
  (testing "successfully geocodes a city name"
    (with-redefs [http/get (fn [_ _] mock-nominatim-response)]
      (let [result (discovery/geocode "San Jose, CA")]
        (is (some? result))
        (is (= 37.3382 (:lat result)))
        (is (= -121.8863 (:lng result)))
        (is (string? (:formatted_address result)))
        (is (= "San Jose, CA" (:input result))))))

  (testing "successfully geocodes a zipcode with USA bias"
    (with-redefs [http/get (fn [url _]
                             (is (clojure.string/includes? url "95112%2C+USA"))
                             mock-nominatim-response)]
      (let [result (discovery/geocode "95112")]
        (is (some? result)))))

  (testing "returns nil for empty results"
    (with-redefs [http/get (fn [_ _] mock-nominatim-empty-response)]
      (let [result (discovery/geocode "Nonexistent Place XYZ")]
        (is (nil? result)))))

  (testing "returns nil on HTTP error"
    (with-redefs [http/get (fn [_ _] mock-nominatim-error-response)]
      (let [result (discovery/geocode "San Jose, CA")]
        (is (nil? result))))))

(def mock-overpass-response
  {:status 200
   :body   "{\"elements\":[{\"id\":123,\"type\":\"way\",\"tags\":{\"name\":\"Test Park\",\"leisure\":\"park\"}},{\"id\":456,\"type\":\"node\",\"tags\":{\"name\":\"Test Cafe\",\"amenity\":\"cafe\",\"cuisine\":\"coffee\"}},{\"id\":789,\"type\":\"way\",\"tags\":{}}]}"})

(def mock-overpass-empty-response
  {:status 200
   :body   "{\"elements\":[]}"})

(def mock-overpass-timeout-response
  {:status 504
   :body   "Gateway Timeout"})

(deftest find-venues-test
  (testing "successfully finds venues near coordinates"
    (with-redefs [http/post (fn [_ _] mock-overpass-response)]
      (let [result (discovery/find-venues {:lat 37.3382 :lng -121.8863})]
        (is (seq result))
        (is (= 2 (count result))) ;; Only 2 have names
        (is (some #(= "Test Park" (:name %)) result))
        (is (some #(= "Test Cafe" (:name %)) result)))))

  (testing "filters out venues without names"
    (with-redefs [http/post (fn [_ _] mock-overpass-response)]
      (let [result (discovery/find-venues {:lat 37.3382 :lng -121.8863})]
        (is (every? :name result)))))

  (testing "extracts venue type from tags"
    (with-redefs [http/post (fn [_ _] mock-overpass-response)]
      (let [result (discovery/find-venues {:lat 37.3382 :lng -121.8863})
            park   (first (filter #(= "Test Park" (:name %)) result))
            cafe   (first (filter #(= "Test Cafe" (:name %)) result))]
        (is (= "park" (:type park)))
        (is (= "cafe" (:type cafe))))))

  (testing "returns nil on empty results"
    (with-redefs [http/post (fn [_ _] mock-overpass-empty-response)]
      (let [result (discovery/find-venues {:lat 37.3382 :lng -121.8863})]
        (is (empty? result)))))

  (testing "returns nil on timeout"
    (with-redefs [http/post (fn [_ _] mock-overpass-timeout-response)]
      (let [result (discovery/find-venues {:lat 37.3382 :lng -121.8863})]
        (is (nil? result)))))

  (testing "calculates appropriate radius from bounds"
    (with-redefs [http/post (fn [_ opts]
                              ;; Verify query contains a reasonable radius
                              (let [query (get-in opts [:form-params :data])]
                                (is (clojure.string/includes? query "around:")))
                              mock-overpass-response)]
      (discovery/find-venues {:lat    37.3382
                              :lng    -121.8863
                              :bounds {:southwest {:lat 37.1 :lng -122.0}
                                       :northeast {:lat 37.5 :lng -121.7}}}))))

(def mock-nws-points-response
  {:status 200
   :body   "{\"properties\":{\"forecast\":\"https://api.weather.gov/gridpoints/MTR/99,83/forecast\"}}"})

(def mock-nws-forecast-response
  {:status 200
   :body   "{\"properties\":{\"periods\":[{\"temperature\":65,\"temperatureUnit\":\"F\",\"windSpeed\":\"10 mph\",\"windDirection\":\"NW\",\"shortForecast\":\"Sunny\",\"detailedForecast\":\"Clear skies\",\"isDaytime\":true,\"name\":\"Today\"}]}}"})

(def mock-nws-error-response
  {:status 500
   :body   "Internal Server Error"})

(deftest fetch-weather-test
  (testing "successfully fetches weather for coordinates"
    (let [call-count (atom 0)]
      (with-redefs [http/get (fn [url _]
                               (swap! call-count inc)
                               (cond
                                 ;; Check gridpoints FIRST since "points" is substring of "gridpoints"
                                 (clojure.string/includes? url "gridpoints")
                                 mock-nws-forecast-response

                                 (clojure.string/includes? url "/points/")
                                 mock-nws-points-response

                                 :else
                                 mock-nws-error-response))]
        (let [result (discovery/fetch-weather {:lat 37.3382 :lng -121.8863})]
          (is (= 2 @call-count) "Should make two API calls")
          (is (some? result))
          (is (= 65 (:temperature result)))
          (is (= "F" (:temperature_unit result)))
          (is (= "10 mph" (:wind_speed result)))
          (is (= "NW" (:wind_direction result)))
          (is (= "Sunny" (:short_forecast result)))
          (is (= true (:is_daytime result)))))))

  (testing "returns nil on points API error"
    (with-redefs [http/get (fn [_ _] mock-nws-error-response)]
      (let [result (discovery/fetch-weather {:lat 37.3382 :lng -121.8863})]
        (is (nil? result)))))

  (testing "returns nil when forecast URL missing"
    (with-redefs [http/get (fn [_ _] {:status 200 :body "{\"properties\":{}}"})]
      (let [result (discovery/fetch-weather {:lat 37.3382 :lng -121.8863})]
        (is (nil? result))))))

;; =============================================================================
;; Integration Tests (all components together, still mocked)
;; =============================================================================

(deftest generate-suggestion-test
  (testing "generates complete suggestion when all services respond"
    (with-redefs [http/get  (fn [url _]
                              (cond
                                (clojure.string/includes? url "gridpoints")
                                mock-nws-forecast-response

                                (clojure.string/includes? url "/points/")
                                mock-nws-points-response

                                :else
                                mock-nominatim-response))
                  http/post (fn [_ _] mock-overpass-response)]
      (let [geo    {:lat 37.3382 :lng -121.8863}
            result (discovery/generate-suggestion geo)]
        (is (some? result))
        (is (contains? result :place))
        (is (contains? result :activity))
        (is (contains? result :weather))
        (is (contains? result :time))
        (is (string? (get-in result [:place :name])))
        (is (string? (get-in result [:activity :activity]))))))

  (testing "returns nil when weather fetch fails"
    (with-redefs [http/get  (fn [_ _] mock-nws-error-response)
                  http/post (fn [_ _] mock-overpass-response)]
      (let [geo    {:lat 37.3382 :lng -121.8863}
            result (discovery/generate-suggestion geo)]
        (is (nil? result)))))

  (testing "returns nil when no venues found"
    (with-redefs [http/get  (fn [url _]
                              (cond
                                (clojure.string/includes? url "gridpoints")
                                mock-nws-forecast-response

                                (clojure.string/includes? url "/points/")
                                mock-nws-points-response

                                :else
                                mock-nws-error-response))
                  http/post (fn [_ _] mock-overpass-empty-response)]
      (let [geo    {:lat 37.3382 :lng -121.8863}
            result (discovery/generate-suggestion geo)]
        (is (nil? result))))))
