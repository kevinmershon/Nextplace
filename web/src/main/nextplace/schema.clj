(ns nextplace.schema
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [com.walmartlabs.lacinia.schema :as schema]
            [com.walmartlabs.lacinia.util :refer [attach-resolvers]]
            [integrant.core :as ig]
            [nextplace.resolvers :as resolvers]))

(defn load-schema
  []
  (-> (io/resource "schema.edn")
      slurp
      edn/read-string
      (attach-resolvers (resolvers/resolver-map))
      schema/compile))

(defmethod ig/init-key :nextplace/schema [_ _]
  (load-schema))
