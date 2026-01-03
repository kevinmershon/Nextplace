(ns nextplace.console.util
  (:require [clojure.string :as str]))

(def ^:private available-consoles
  {:rocksdb 'nextplace.console.rocksdb})

(defn list-consoles
  []
  (keys available-consoles))

(defn get-console-ns
  [console-kw]
  (get available-consoles console-kw))

(defn list-public-vars
  [ns-sym]
  (let [ns-obj (find-ns ns-sym)]
    (when ns-obj
      (->> (ns-publics ns-obj)
           (map (fn [[sym var]]
                  (let [m (meta var)]
                    {:name     sym
                     :doc      (:doc m)
                     :arglists (:arglists m)})))
           (remove (fn [{:keys [name]}]
                     (str/starts-with? (str name) "-")))
           (sort-by :name)))))

(defn format-command
  [{:keys [name arglists doc]}]
  (let [args-str (if arglists
                   (str/join " or " (map pr-str arglists))
                   "")
        fn-call  (str "(" name (when (seq args-str) " ") args-str ")")]
    (str fn-call
         (when doc
           (str "\n   - " (str/replace doc #"\n" "\n     "))))))
