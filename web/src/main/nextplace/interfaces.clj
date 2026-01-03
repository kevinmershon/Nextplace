(ns nextplace.interfaces
  (:require [clojure.tools.logging :as log]
            [integrant.core :as ig]))

(defmethod ig/init-key :nextplace/interfaces [_ config]
  (log/info "External interfaces initialized" {:apis (keys config)})
  config)

(defmethod ig/halt-key! :nextplace/interfaces [_ _]
  (log/info "External interfaces stopped"))
