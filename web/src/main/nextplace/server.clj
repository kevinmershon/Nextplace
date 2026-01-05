(ns nextplace.server
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [com.walmartlabs.lacinia :as lacinia]
            [integrant.core :as ig]
            [nextplace.discovery :as discovery]
            [nextplace.schema :as schema]
            [reitit.ring :as ring]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.util.mime-type :as mime]))

(defn graphql-handler [schema-component]
  (fn [request]
    (let [body       (slurp (:body request))
          query-data (json/read-str body :key-fn keyword)
          context    {:email    (:email schema-component)
                      :auth     (:auth schema-component)
                      :base-url (:base-url schema-component)}
          result     (lacinia/execute (:compiled-schema schema-component)
                                      (:query query-data)
                                      (:variables query-data)
                                      context)]
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
  (let [db               (nextplace.db/open-db "data/nextplace.db")
        _                (ig/init-key :nextplace/discovery {:db db})
        email-config     {:provider :postmark
                          :api-key  (get (System/getenv) "POSTMARK_API_KEY")
                          :from     "hello@nextplace.app"}
        auth-config      {:token-ttl-minutes 15
                          :session-ttl-days  7}
        schema-compiled  (schema/load-schema db email-config auth-config)
        schema-component {:compiled-schema schema-compiled
                          :email           email-config
                          :auth            auth-config
                          :base-url        (get (System/getenv) "BASE_URL" "http://localhost:8888")}
        server           (create-server {:schema schema-component
                                         :port   8888})]
    (println "GraphQL server running on http://localhost:8888/graphql")
    @(promise)))
