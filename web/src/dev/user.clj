(ns user
  (:require
   [clojure.java.io :as io]
   [integrant.core :as ig]
   [integrant.repl :as ig-repl]
   [integrant.repl.state :as state]
   [nextplace.console.util :as console-util]))

(ig-repl/set-prep! (fn [] (-> "config.edn" io/resource slurp ig/read-string)))

(defn go
  "Load all component namespaces and start the system"
  []
  (require 'nextplace.server)
  (require 'nextplace.scraper-job)
  (ig-repl/go))

(defn halt
  "Stop the system"
  []
  (ig-repl/halt))

(defn reset
  "Reload code and restart the system"
  []
  (ig-repl/reset))

(def clear ig-repl/clear)
(def prep ig-repl/prep)
(def init ig-repl/init)
(def reset-all ig-repl/reset-all)

(defn db
  "Get the database component from the running system"
  []
  (get state/system :nextplace/db))

(defn select
  "Select a console namespace to work in.
   Usage: (select :rocksdb)"
  [console-kw]
  (if-let [console-ns (console-util/get-console-ns console-kw)]
    (do
      (require console-ns)
      (let [set-ns-fn (ns-resolve console-ns 'set-top-level-ns!)]
        (when set-ns-fn
          (set-ns-fn 'user)))
      (in-ns console-ns)
      (println "Entered" console-ns "console. Run (commands) to see available commands.")
      (println "Run (back) to return to user namespace."))
    (do
      (println "Unknown console:" console-kw)
      (println "Available consoles:" (console-util/list-consoles)))))

(defn commands
  "List available console commands"
  []
  (let [commands {"(select :console-kw)"
                  (str "select a console, one of " (console-util/list-consoles))
                  "(commands)" "list commands"
                  "(go)"       "start server and initialize components"
                  "(halt)"     "stop server and cleanup components"
                  "(reset)"    "reload code and restart"
                  "(db)"       "get the database component"}]
    (println "\nAvailable Commands:\n")
    (doseq [[fn-name command] commands]
      (println fn-name)
      (println "   -" command "\n")))
  :ok)

(println "\nNextplace REPL ready.")
(println "Run (go) to start the server.")
(println "Run (commands) to see available commands.")
(println "Run (select :rocksdb) to enter the RocksDB console.")
(println "Run (select :scraper) to enter the Scraper console.\n")
