(ns nextplace.schema
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [com.walmartlabs.lacinia.schema :as schema]
            [com.walmartlabs.lacinia.util :refer [attach-resolvers]]
            [integrant.core :as ig]
            [nextplace.auth]
            [nextplace.email]
            [nextplace.interfaces]
            [nextplace.redis]
            [nextplace.resolvers :as resolvers]))

(defn load-schema
  [db email auth]
  (-> (io/resource "schema.edn")
      slurp
      edn/read-string
      (attach-resolvers (resolvers/resolver-map db))
      schema/compile))

(defmethod ig/init-key :nextplace/schema [_ {:keys [db email auth interfaces]}]
  (let [email-with-key (assoc email :api-key (get (System/getenv) "POSTMARK_API_KEY"))]
    {:compiled-schema (load-schema db email-with-key auth)
     :email           email-with-key
     :auth            auth
     :interfaces      interfaces
     :base-url        (get (System/getenv) "BASE_URL" "http://localhost:8888")}))
