(ns mcp
  (:require
   [clojure-mcp.main]))

(defn connect [_]
  (when-let [port (some->> (slurp ".nrepl-port")
                           (parse-long))]
    (println "Found port" port)
    (clojure-mcp.main/start-mcp-server {:port port})))
