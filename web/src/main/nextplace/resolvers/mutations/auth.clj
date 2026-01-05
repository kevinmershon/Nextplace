(ns nextplace.resolvers.mutations.auth
  (:require [clojure.tools.logging :as log]
            [nextplace.auth :as auth]
            [nextplace.db :as db]
            [nextplace.email :as email])
  (:import [java.time Instant]
           [java.util UUID]))

(defn create-new-user
  [email]
  {:id                          (str (UUID/randomUUID))
   :email                       email
   :name                        email
   :signed_up_at                (str (Instant/now))
   :flow3_unlocked              false
   :completed_experiences_count 0})

(defn signup
  [db context args value]
  (let [email     (:email args)
        user-data (create-new-user email)]
    (db/put-value db (str "user:" email) user-data)
    user-data))

(defn auth-request
  [db context args value]
  (let [email        (:email args)
        email-config (:email context)
        auth-config  (:auth context)
        base-url     (:base-url context)
        ttl-minutes  (:token-ttl-minutes auth-config)
        token-result (auth/create-auth-token db email ttl-minutes)
        send-result  (email/send-magic-link email-config base-url (:token token-result) email)]
    (if (:success send-result)
      {:email      email
       :message    "Magic link sent to your email"
       :expires_at (:expires_at token-result)}
      (do
        (log/error "Failed to send magic link" {:email email :error (:error send-result)})
        (throw (ex-info "Failed to send authentication email" {:email email}))))))

(defn auth-verify
  [db context args value]
  (let [token       (:token args)
        auth-config (:auth context)
        email       (auth/verify-auth-token db token)]
    (if email
      (let [user-data (or (db/get-value db (str "user:" email))
                          (let [new-user (create-new-user email)]
                            (db/put-value db (str "user:" email) new-user)
                            new-user))
            session   (auth/create-session-token auth-config email (:id user-data))]
        {:user          user-data
         :session_token (:token session)
         :expires_at    (:expires_at session)})
      (do
        (log/warn "Invalid or expired auth token" {:token token})
        (throw (ex-info "Invalid or expired authentication token" {:token token}))))))

(defn update-profile
  "Update user profile fields like name and gender."
  [db context args value]
  ;; TODO: Get current user from session in context
  ;; For now, require email in context or throw
  (let [;; Placeholder - get user email from session when auth is wired up
        user-email "placeholder@example.com"
        user-key   (str "user:" user-email)
        user-data  (db/get-value db user-key)]
    (if user-data
      (let [updated-user (cond-> user-data
                           (:name args) (assoc :name (:name args))
                           (:gender args) (assoc :gender (:gender args)))]
        (db/put-value db user-key updated-user)
        updated-user)
      (throw (ex-info "User not found" {:type :not-found})))))
