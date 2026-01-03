(ns nextplace.console.rocksdb
  (:require [clojure.edn :as edn]
            [clojure.pprint :refer [pprint]]
            [clojure.string :as str]
            [integrant.repl.state :as state]
            [nextplace.console.util :as util]
            [nextplace.db :as db])
  (:import [org.rocksdb RocksIterator]))

(defonce ^:private back-ns (atom 'user))

(defn ^:private get-db
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
  (let [vars         (util/list-public-vars 'nextplace.console.rocksdb)
        nav-commands {"(back)"     "exit back to initial namespace"
                      "(commands)" "list commands"}]
    (println "\nRocksDB Console Commands:\n")
    (doseq [[fn-str desc] nav-commands]
      (println fn-str)
      (println "   -" desc "\n"))
    (doseq [v vars]
      (println (util/format-command v) "\n")))
  :ok)

(defn list-keys
  "List all keys in the database, optionally filtered by prefix"
  ([]
   (list-keys nil))
  ([prefix]
   (let [db-instance (get-db)
         iterator    ^RocksIterator (.newIterator db-instance)
         keys        (atom [])]
     (try
       (.seekToFirst iterator)
       (while (.isValid iterator)
         (let [key (String. (.key iterator) "UTF-8")]
           (when (or (nil? prefix) (str/starts-with? key prefix))
             (swap! keys conj key))
           (.next iterator)))
       (if (empty? @keys)
         (println "No keys found" (when prefix (str "with prefix: " prefix)))
         (do
           (println "Found" (count @keys) "keys:")
           (doseq [k (sort @keys)]
             (println "  " k))))
       @keys
       (finally
         (.close iterator))))))

(defn get-value
  "Get the value for a specific key"
  [key]
  (let [db-instance (get-db)]
    (if-let [value (db/get-value db-instance key)]
      (do
        (println "Key:" key)
        (println "Value:")
        (pprint value)
        value)
      (do
        (println "Key not found:" key)
        nil))))

(defn put-value
  "Store a value at the specified key"
  [key value]
  (let [db-instance  (get-db)
        parsed-value (if (string? value)
                       (try
                         (edn/read-string value)
                         (catch Exception _e value))
                       value)]
    (db/put-value db-instance key parsed-value)
    (println "Stored" key "=>" parsed-value)
    parsed-value))

(defn delete-key
  "Delete a key from the database"
  [key]
  (let [db-instance (get-db)]
    (if (db/get-value db-instance key)
      (do
        (db/delete-value db-instance key)
        (println "Deleted key:" key)
        true)
      (do
        (println "Key not found:" key)
        false))))

(defn scan
  "Scan and display all keys with the given prefix"
  [prefix]
  (let [db-instance (get-db)
        iterator    ^RocksIterator (.newIterator db-instance)
        results     (atom [])]
    (try
      (.seekToFirst iterator)
      (while (.isValid iterator)
        (let [key   (String. (.key iterator) "UTF-8")
              value (when (or (nil? prefix) (str/starts-with? key prefix))
                      (db/get-value db-instance key))]
          (when value
            (swap! results conj {:key key :value value}))
          (.next iterator)))
      (if (empty? @results)
        (println "No entries found" (when prefix (str "with prefix: " prefix)))
        (do
          (println "Found" (count @results) "entries:")
          (doseq [{:keys [key value]} @results]
            (println "\n" key "=>")
            (pprint value))))
      @results
      (finally
        (.close iterator)))))

(defn count-keys
  "Count total keys in the database, optionally filtered by prefix"
  ([]
   (count-keys nil))
  ([prefix]
   (let [db-instance (get-db)
         iterator    ^RocksIterator (.newIterator db-instance)
         count       (atom 0)]
     (try
       (.seekToFirst iterator)
       (while (.isValid iterator)
         (let [key (String. (.key iterator) "UTF-8")]
           (when (or (nil? prefix) (str/starts-with? key prefix))
             (swap! count inc))
           (.next iterator)))
       (println "Total keys:" @count (when prefix (str "(prefix: " prefix ")")))
       @count
       (finally
         (.close iterator))))))

(defn stats
  "Show database statistics including key counts by prefix"
  []
  (let [db-instance (get-db)
        iterator    ^RocksIterator (.newIterator db-instance)
        prefixes    (atom {})
        total       (atom 0)]
    (try
      (.seekToFirst iterator)
      (while (.isValid iterator)
        (let [key    (String. (.key iterator) "UTF-8")
              prefix (first (str/split key #":"))]
          (swap! total inc)
          (swap! prefixes update prefix (fnil inc 0))
          (.next iterator)))
      (println "Database Statistics:")
      (println "  Total keys:" @total)
      (println "  Keys by prefix:")
      (doseq [[prefix count] (sort-by second > @prefixes)]
        (println "   " prefix ":" count))
      {:total @total :prefixes @prefixes}
      (finally
        (.close iterator)))))
