(ns nextplace.scraper-job
  "Quartzite job that runs all Crawlee scraper scripts.
   Executes each script in the scrapers/ directory, parses JSON output,
   and stores extracted events in RocksDB."
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [clojurewerkz.quartzite.jobs :as jobs :refer [defjob]]
            [clojurewerkz.quartzite.scheduler :as scheduler]
            [clojurewerkz.quartzite.schedule.cron :as cron]
            [clojurewerkz.quartzite.triggers :as triggers]
            [integrant.core :as ig]
            [nextplace.db :as db])
  (:import [java.io File]
           [java.security MessageDigest]
           [java.time Instant]))

;; =============================================================================
;; Script Execution
;; =============================================================================

(defn- scrapers-dir
  "Get the scrapers directory path"
  []
  (let [candidates ["scrapers"
                    "../scrapers"
                    "../../scrapers"]]
    (first (filter #(.isDirectory (io/file %)) candidates))))

(defn- list-scraper-scripts
  "List all .js files in the scrapers directory"
  []
  (when-let [dir (scrapers-dir)]
    (->> (io/file dir)
         (.listFiles)
         (filter #(and (.isFile %)
                       (str/ends-with? (.getName %) ".js")))
         (map #(.getAbsolutePath %)))))

(defn- run-script
  "Execute a Node.js script and capture its stdout.
   Returns {:success boolean :output string :error string}"
  [script-path]
  (log/info "Running scraper:" script-path)
  (try
    (let [process     (-> (ProcessBuilder. ["node" script-path])
                          (.redirectErrorStream false)
                          (.start))
          stdout      (slurp (.getInputStream process))
          stderr      (slurp (.getErrorStream process))
          exit-code   (.waitFor process)]
      (if (zero? exit-code)
        {:success true
         :output  stdout
         :error   nil}
        {:success false
         :output  stdout
         :error   (str "Exit code " exit-code ": " stderr)}))
    (catch Exception e
      {:success false
       :output  nil
       :error   (str "Exception: " (.getMessage e))})))

(defn- parse-events
  "Parse JSON output from a scraper script"
  [json-str]
  (try
    (let [events (json/read-str json-str :key-fn keyword)]
      (if (sequential? events)
        events
        (do
          (log/warn "Script output is not an array")
          [])))
    (catch Exception e
      (log/error "Failed to parse script output as JSON:" (.getMessage e))
      [])))

;; =============================================================================
;; Event Storage
;; =============================================================================

(defn- event-hash
  "Generate a hash for an event to detect duplicates"
  [event]
  (let [content (str (:title event) (:date event) (:time event) (:location event))
        digest  (MessageDigest/getInstance "SHA-256")
        bytes   (.digest digest (.getBytes content "UTF-8"))]
    (->> bytes
         (map #(format "%02x" %))
         (apply str)
         (take 16)
         (apply str))))

(defn- store-event
  "Store a scraped event in RocksDB"
  [db-instance source-id event]
  (let [hash    (event-hash event)
        key     (str "scraped_event:" source-id ":" hash)
        record  (assoc event
                       :source_id  source-id
                       :scraped_at (str (Instant/now)))]
    (db/put-value db-instance key record)
    record))

(defn- source-id-from-script
  "Extract source ID from script filename"
  [script-path]
  (-> (io/file script-path)
      (.getName)
      (str/replace #"\.js$" "")))

;; =============================================================================
;; Job Implementation
;; =============================================================================

(defonce ^:private job-db (atom nil))

(defn run-all-scrapers
  "Run all scraper scripts and store events.
   This is the main entry point, callable from the job or console."
  [db-instance]
  (let [scripts (list-scraper-scripts)]
    (if (empty? scripts)
      (do
        (log/info "No scraper scripts found in scrapers/ directory")
        {:scripts-run   0
         :events-stored 0})
      (let [results (atom {:scripts-run   0
                           :events-stored 0
                           :errors        []})]
        (doseq [script scripts]
          (let [source-id (source-id-from-script script)
                result    (run-script script)]
            (swap! results update :scripts-run inc)
            (if (:success result)
              (let [events (parse-events (:output result))]
                (log/info "Script" script "returned" (count events) "events")
                (doseq [event events]
                  (store-event db-instance source-id event)
                  (swap! results update :events-stored inc)))
              (do
                (log/error "Script" script "failed:" (:error result))
                (swap! results update :errors conj
                       {:script script :error (:error result)})))))
        (log/info "Scraper run complete:" @results)
        @results))))

(defjob ScraperJob
  [ctx]
  (log/info "Starting scheduled scraper job")
  (if-let [db @job-db]
    (run-all-scrapers db)
    (log/error "Database not available for scraper job")))

;; =============================================================================
;; Integrant Component
;; =============================================================================

(defmethod ig/init-key :nextplace/scraper-job [_ {:keys [db cron-schedule enabled]}]
  (reset! job-db db)
  (if enabled
    (let [sched     (scheduler/initialize)
          job       (jobs/build
                     (jobs/of-type ScraperJob)
                     (jobs/with-identity "scraper-job"))
          trigger   (triggers/build
                     (triggers/with-identity "scraper-trigger")
                     (triggers/with-schedule
                       (cron/schedule
                        (cron/cron-schedule (or cron-schedule "0 0 */6 * * ?")))))]
      (scheduler/start sched)
      (scheduler/schedule sched job trigger)
      (log/info "Scraper job scheduled with cron:" (or cron-schedule "0 0 */6 * * ?"))
      {:scheduler sched :enabled true})
    (do
      (log/info "Scraper job disabled")
      {:scheduler nil :enabled false})))

(defmethod ig/halt-key! :nextplace/scraper-job [_ {:keys [scheduler]}]
  (when scheduler
    (scheduler/shutdown scheduler)
    (log/info "Scraper job scheduler stopped"))
  (reset! job-db nil))
