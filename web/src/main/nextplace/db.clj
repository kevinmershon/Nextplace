(ns nextplace.db
  (:require [clojure.edn :as edn]
            [integrant.core :as ig])
  (:import [org.rocksdb Options RocksDB RocksDBException]))

(defonce db-instance (atom nil))

(defn open-db [path]
  (RocksDB/loadLibrary)
  (let [options (doto (Options.)
                  (.setCreateIfMissing true))]
    (try
      (RocksDB/open options path)
      (catch RocksDBException e
        (throw (ex-info "Failed to open RocksDB" {:path path} e))))))

(defn close-db [db]
  (when db
    (.close db)))

(defn get-value [db key-str]
  (try
    (when-let [bytes (.get db (.getBytes key-str "UTF-8"))]
      (edn/read-string (String. bytes "UTF-8")))
    (catch RocksDBException e
      (throw (ex-info "Failed to read from RocksDB" {:key key-str} e)))))

(defn put-value [db key-str value]
  (try
    (.put db
          (.getBytes key-str "UTF-8")
          (.getBytes (pr-str value) "UTF-8"))
    (catch RocksDBException e
      (throw (ex-info "Failed to write to RocksDB" {:key key-str :value value} e)))))

(defn delete-value [db key-str]
  (try
    (.delete db (.getBytes key-str "UTF-8"))
    (catch RocksDBException e
      (throw (ex-info "Failed to delete from RocksDB" {:key key-str} e)))))

(defmethod ig/init-key :nextplace/db [_ {:keys [path]}]
  (let [db (open-db path)]
    (reset! db-instance db)
    db))

(defmethod ig/halt-key! :nextplace/db [_ db]
  (close-db db)
  (reset! db-instance nil))
