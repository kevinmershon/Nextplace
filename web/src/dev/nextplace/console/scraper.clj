(ns nextplace.console.scraper
  "Console for managing event source scrapers.
   Queue URLs for Claude to generate scraper scripts."
  (:require [clojure.pprint :refer [pprint]]
            [clojure.string :as str]
            [integrant.repl.state :as state]
            [nextplace.console.util :as util]
            [nextplace.db :as db])
  (:import [java.time Instant]
           [java.util UUID]
           [org.rocksdb RocksIterator]))

(defonce ^:private back-ns (atom 'user))

(defn- get-db
  []
  (or (get state/system :nextplace/db)
      (throw (ex-info "Database not available. Run (go) first." {}))))

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
  (let [vars         (util/list-public-vars 'nextplace.console.scraper)
        nav-commands {"(back)"     "exit back to initial namespace"
                      "(commands)" "list commands"}]
    (println "\nScraper Console Commands:\n")
    (doseq [[fn-str desc] nav-commands]
      (println fn-str)
      (println "   -" desc "\n"))
    (doseq [v vars]
      (println (util/format-command v) "\n")))
  :ok)

;; =============================================================================
;; Pending Source Management
;; =============================================================================

(defn queue-source
  "Queue a URL for Claude to generate a scraper script.
   Claude will see this via the nextplace-mcp server.

   Usage:
     (queue-source \"https://shfb.org/volunteer\" \"Second Harvest\" [\"San Jose, CA\"])
     (queue-source \"https://example.com/events\" \"Example Events\" [\"SF\"] \"Look for the calendar widget\")"
  ([url name geographic-scope]
   (queue-source url name geographic-scope nil))
  ([url name geographic-scope notes]
   (let [db-instance (get-db)
         id          (str (UUID/randomUUID))
         source      {:id               id
                      :url              url
                      :name             name
                      :geographic_scope geographic-scope
                      :queued_at        (str (Instant/now))
                      :notes            notes}
         key         (str "pending_source:" id)]
     (db/put-value db-instance key source)
     (println "Queued source for scraper generation:")
     (pprint source)
     (println "\nClaude can now see this via MCP: list_pending_sources")
     source)))

(defn list-pending
  "List all sources pending scraper generation"
  []
  (let [db-instance (get-db)
        iterator    ^RocksIterator (.newIterator db-instance)
        prefix      "pending_source:"
        results     (atom [])]
    (try
      (.seekToFirst iterator)
      (while (.isValid iterator)
        (let [key (String. (.key iterator) "UTF-8")]
          (when (str/starts-with? key prefix)
            (when-let [value (db/get-value db-instance key)]
              (swap! results conj value)))
          (.next iterator)))
      (if (empty? @results)
        (do
          (println "No pending sources.")
          (println "Use (queue-source url name areas) to add one."))
        (do
          (println "Pending sources:" (count @results))
          (doseq [source @results]
            (println "\n" (:id source))
            (println "  URL:" (:url source))
            (println "  Name:" (:name source))
            (println "  Areas:" (:geographic_scope source))
            (when (:notes source)
              (println "  Notes:" (:notes source))))))
      @results
      (finally
        (.close iterator)))))

(defn remove-pending
  "Remove a source from the pending queue (e.g., if no longer needed)"
  [id]
  (let [db-instance (get-db)
        key         (str "pending_source:" id)]
    (if-let [source (db/get-value db-instance key)]
      (do
        (db/delete-value db-instance key)
        (println "Removed pending source:" (:name source))
        source)
      (do
        (println "No pending source found with ID:" id)
        nil))))

;; =============================================================================
;; Configured Sources (after Claude generates scripts)
;; =============================================================================

(defn list-sources
  "List all configured event sources (those with scraper scripts)"
  []
  (let [db-instance (get-db)
        iterator    ^RocksIterator (.newIterator db-instance)
        prefix      "event_source:"
        results     (atom [])]
    (try
      (.seekToFirst iterator)
      (while (.isValid iterator)
        (let [key (String. (.key iterator) "UTF-8")]
          (when (str/starts-with? key prefix)
            (when-let [value (db/get-value db-instance key)]
              (swap! results conj value)))
          (.next iterator)))
      (if (empty? @results)
        (do
          (println "No configured event sources.")
          (println "Queue a URL with (queue-source ...) and have Claude generate a script."))
        (do
          (println "Configured event sources:" (count @results))
          (doseq [source @results]
            (println "\n" (:id source))
            (println "  Name:" (:name source))
            (println "  Script:" (:script_name source))
            (println "  Areas:" (:geographic_scope source))
            (println "  Last run:" (or (:last_run source) "never")))))
      @results
      (finally
        (.close iterator)))))

(defn add-source
  "Register a configured event source after its script has been created.
   This is typically called after Claude generates and saves a scraper script.

   Usage:
     (add-source \"second-harvest\" \"Second Harvest\" \"second-harvest.js\" [\"San Jose, CA\"])"
  [id name script-name geographic-scope]
  (let [db-instance (get-db)
        source      {:id               id
                     :name             name
                     :script_name      script-name
                     :geographic_scope geographic-scope
                     :created_at       (str (Instant/now))
                     :active           true}
        key         (str "event_source:" id)]
    (db/put-value db-instance key source)
    (println "Registered event source:")
    (pprint source)
    source))

(defn disable-source
  "Disable an event source (stops polling)"
  [id]
  (let [db-instance (get-db)
        key         (str "event_source:" id)]
    (if-let [source (db/get-value db-instance key)]
      (let [updated (assoc source :active false)]
        (db/put-value db-instance key updated)
        (println "Disabled source:" (:name source))
        updated)
      (do
        (println "No source found with ID:" id)
        nil))))

(defn enable-source
  "Enable an event source (resumes polling)"
  [id]
  (let [db-instance (get-db)
        key         (str "event_source:" id)]
    (if-let [source (db/get-value db-instance key)]
      (let [updated (assoc source :active true)]
        (db/put-value db-instance key updated)
        (println "Enabled source:" (:name source))
        updated)
      (do
        (println "No source found with ID:" id)
        nil))))

;; =============================================================================
;; Manual Scraper Execution
;; =============================================================================

(defn run-scrapers
  "Manually run all scraper scripts now.
   Executes each .js file in scrapers/ and stores extracted events."
  []
  (require 'nextplace.scraper-job)
  (let [run-fn (resolve 'nextplace.scraper-job/run-all-scrapers)]
    (println "Running all scrapers...")
    (let [result (run-fn (get-db))]
      (println "\nResults:")
      (println "  Scripts run:" (:scripts-run result))
      (println "  Events stored:" (:events-stored result))
      (when (seq (:errors result))
        (println "  Errors:" (count (:errors result)))
        (doseq [err (:errors result)]
          (println "    -" (:script err) ":" (:error err))))
      result)))

(defn list-events
  "List scraped events, optionally filtered by source"
  ([]
   (list-events nil))
  ([source-id]
   (let [db-instance (get-db)
         iterator    ^RocksIterator (.newIterator db-instance)
         prefix      (if source-id
                       (str "scraped_event:" source-id ":")
                       "scraped_event:")
         results     (atom [])]
     (try
       (.seekToFirst iterator)
       (while (.isValid iterator)
         (let [key (String. (.key iterator) "UTF-8")]
           (when (str/starts-with? key prefix)
             (when-let [value (db/get-value db-instance key)]
               (swap! results conj value)))
           (.next iterator)))
       (if (empty? @results)
         (println "No scraped events found.")
         (do
           (println "Scraped events:" (count @results))
           (doseq [event (take 20 @results)]
             (println "\n  Title:" (:title event))
             (println "  Date:" (:date event) (:time event))
             (println "  Location:" (:location event))
             (println "  Source:" (:source_id event)))
           (when (> (count @results) 20)
             (println "\n  ... and" (- (count @results) 20) "more"))))
       @results
       (finally
         (.close iterator))))))
