(ns nextplace.server
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [com.walmartlabs.lacinia :as lacinia]
            [integrant.core :as ig]
            [nextplace.db :as db]
            [nextplace.discovery :as discovery]
            [nextplace.schema :as schema]
            [reitit.ring :as ring]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.util.mime-type :as mime])
  (:import [org.rocksdb RocksIterator]))

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

;; =============================================================================
;; MCP API Handlers - For Rust MCP server communication
;; =============================================================================

(defn- list-pending-sources-from-db
  "List all pending sources from RocksDB"
  [db-instance]
  (let [iterator ^RocksIterator (.newIterator db-instance)
        prefix   "pending_source:"
        results  (atom [])]
    (try
      (.seekToFirst iterator)
      (while (.isValid iterator)
        (let [key (String. (.key iterator) "UTF-8")]
          (when (str/starts-with? key prefix)
            (when-let [value (db/get-value db-instance key)]
              (swap! results conj value)))
          (.next iterator)))
      @results
      (finally
        (.close iterator)))))

(defn pending-sources-handler
  "GET /api/pending-sources - List pending sources for MCP server"
  [db-instance]
  (fn [_request]
    {:status  200
     :headers {"Content-Type" "application/json"}
     :body    (json/write-str (list-pending-sources-from-db db-instance))}))

(defn mark-source-complete-handler
  "POST /api/pending-sources/:id/complete - Mark source complete"
  [db-instance]
  (fn [request]
    (let [id  (get-in request [:path-params :id])
          key (str "pending_source:" id)]
      (if-let [source (db/get-value db-instance key)]
        (do
          (db/delete-value db-instance key)
          {:status  200
           :headers {"Content-Type" "application/json"}
           :body    (json/write-str {:message "Source marked as complete"
                                     :id      id
                                     :name    (:name source)})})
        {:status  404
         :headers {"Content-Type" "application/json"}
         :body    (json/write-str {:error (str "No pending source found with ID: " id)})}))))

(defn create-handler [schema db-instance]
  (-> (ring/ring-handler
       (ring/router
        [["/" {:get index-handler}]
         ["/graphql" {:post (graphql-handler schema)}]
         ;; MCP API endpoints
         ["/api/pending-sources" {:get (pending-sources-handler db-instance)}]
         ["/api/pending-sources/:id/complete" {:post (mark-source-complete-handler db-instance)}]])
       (ring/routes
        (ring/create-resource-handler {:path "/"
                                       :root "public"})
        (ring/create-default-handler
         {:not-found (constantly {:status  404
                                  :headers {"Content-Type" "text/plain"}
                                  :body    "Not found"})})))
      wrap-content-type))

(defn create-server [{:keys [schema port db]}]
  (jetty/run-jetty (create-handler schema db)
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
  (let [db               (db/open-db "data/nextplace.db")
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
                                         :port   8888
                                         :db     db})]
    (println "GraphQL server running on http://localhost:8888/graphql")
    (println "MCP API available at http://localhost:8888/api/pending-sources")
    @(promise)))
