(ns nextplace.auth
  (:require [buddy.core.codecs :as codecs]
            [buddy.core.nonce :as nonce]
            [buddy.sign.jwt :as jwt]
            [clj-time.core :as time]
            [clj-time.coerce :as tc]
            [clojure.tools.logging :as log]
            [integrant.core :as ig]
            [nextplace.db :as db]))

(defn generate-token
  "Generate cryptographically random token"
  []
  (codecs/bytes->hex (nonce/random-bytes 32)))

(defn create-auth-token
  "Create and store auth token for email"
  [db email ttl-minutes]
  (let [token      (generate-token)
        now        (time/now)
        expires-at (time/plus now (time/minutes ttl-minutes))
        token-data {:token      token
                    :email      email
                    :created_at (str now)
                    :expires_at (str expires-at)
                    :used       false}]
    (db/put-value db (str "auth_token:" token) token-data)
    {:token token :expires_at (str expires-at)}))

(defn verify-auth-token
  "Verify and consume auth token"
  [db token]
  (when-let [token-data (db/get-value db (str "auth_token:" token))]
    (let [now        (time/now)
          expires-at (tc/from-string (:expires_at token-data))]
      (cond
        (:used token-data)
        (do
          (log/warn "Token already used" {:token token})
          nil)

        (time/after? now expires-at)
        (do
          (log/warn "Token expired" {:token token})
          (db/delete-value db (str "auth_token:" token))
          nil)

        :else
        (do
          (db/put-value db (str "auth_token:" token)
                        (assoc token-data :used true))
          (:email token-data))))))

(defn create-session-token
  "Create JWT session token for user"
  [auth-config email user-id]
  (let [secret     (get (System/getenv) "JWT_SECRET")
        ttl-days   (:session-ttl-days auth-config)
        now        (time/now)
        expires-at (time/plus now (time/days ttl-days))
        claims     {:user_id email
                    :email   email
                    :iat     (tc/to-epoch now)
                    :exp     (tc/to-epoch expires-at)}]
    (when-not secret
      (throw (ex-info "JWT_SECRET not configured" {})))
    {:token      (jwt/sign claims secret)
     :expires_at (str expires-at)}))

(defn verify-session-token
  "Verify JWT session token"
  [token]
  (try
    (let [secret (get (System/getenv) "JWT_SECRET")]
      (when-not secret
        (throw (ex-info "JWT_SECRET not configured" {})))
      (jwt/unsign token secret))
    (catch Exception e
      (log/warn "Invalid session token" {:error (.getMessage e)})
      nil)))

(defmethod ig/init-key :nextplace/auth [_ config]
  (log/info "Auth service initialized" {:token-ttl (:token-ttl-minutes config)})
  config)

(defmethod ig/halt-key! :nextplace/auth [_ _]
  (log/info "Auth service stopped"))
