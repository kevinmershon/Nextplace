(ns user
  (:require
   [clojure.java.io :as io]
   [integrant.core :as ig]
   [integrant.repl :refer [clear go halt prep init reset reset-all]]
   [integrant.repl.state :as state]))

(integrant.repl/set-prep! (fn [] (-> "config.edn" io/resource slurp ig/read-string)))

(println "\nNextplace REPL ready.")
(println "Run (go) to start the server.")
(println "Run (halt) to stop the server.")
(println "Run (reset) to reload and restart.\n")
