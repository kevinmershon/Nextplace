(ns nextplace.redis
  (:require [clojure.tools.logging :as log]
            [integrant.core :as ig]
            [taoensso.carmine :as car]))

(defn connection-pool
  [uri]
  {:pool {} :spec {:uri uri}})

(defmethod ig/init-key :nextplace/redis [_ {:keys [uri]}]
  (log/info "Redis connection initialized" {:uri uri})
  (connection-pool uri))

(defmethod ig/halt-key! :nextplace/redis [_ _conn]
  (log/info "Redis connection closed"))
