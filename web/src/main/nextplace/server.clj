(ns nextplace.server
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [com.walmartlabs.lacinia :as lacinia]
            [integrant.core :as ig]
            [nextplace.schema :as schema]
            [reitit.ring :as ring]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.util.mime-type :as mime]))

(defn graphql-handler [schema]
  (fn [request]
    (let [body       (slurp (:body request))
          query-data (json/read-str body :key-fn keyword)
          result     (lacinia/execute schema
                                      (:query query-data)
                                      (:variables query-data)
                                      nil)]
      {:status  200
       :headers {"Content-Type" "application/json"}
       :body    (json/write-str result)})))

(defn index-handler [_]
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    (slurp (io/resource "public/index.html"))})

(defn create-handler [schema]
  (-> (ring/ring-handler
       (ring/router
        [["/" {:get index-handler}]
         ["/graphql" {:post (graphql-handler schema)}]])
       (ring/routes
        (ring/create-resource-handler {:path "/"
                                       :root "public"})
        (ring/create-default-handler
         {:not-found (constantly {:status  404
                                  :headers {"Content-Type" "text/plain"}
                                  :body    "Not found"})})))
      wrap-content-type))

(defn create-server [{:keys [schema port]}]
  (jetty/run-jetty (create-handler schema)
                   {:port  port
                    :join? false}))

(defmethod ig/init-key :nextplace/server [_ config]
  (let [server (create-server config)]
    (println "GraphQL server running on http://localhost:" (:port config) "/graphql")
    server))

(defmethod ig/halt-key! :nextplace/server [_ server]
  (.stop server)
  (println "GraphQL server stopped"))

(defn -main
  [& args]
  (let [db     (nextplace.db/open-db "data/nextplace.db")
        schema (schema/load-schema db)
        server (create-server {:schema schema
                               :port   8888})]
    (println "GraphQL server running on http://localhost:8888/graphql")
    @(promise)))
