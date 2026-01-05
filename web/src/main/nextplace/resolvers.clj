(ns nextplace.resolvers
  (:require [nextplace.resolvers.mutations.auth :as mut-auth]
            [nextplace.resolvers.mutations.experience :as mut-experience]
            [nextplace.resolvers.mutations.social :as mut-social]
            [nextplace.resolvers.mutations.suggestion :as mut-suggestion]
            [nextplace.resolvers.queries.experience :as q-experience]
            [nextplace.resolvers.queries.social :as q-social]
            [nextplace.resolvers.queries.suggestion :as q-suggestion]
            [nextplace.resolvers.queries.user :as q-user]
            [nextplace.resolvers.queries.weather :as q-weather]))

(defmulti resolve-query
  (fn [field-name context args value] field-name))

(defmulti resolve-mutation
  (fn [field-name db context args value] field-name))

(defmethod resolve-query :current-suggestion
  [_ context args value]
  (q-suggestion/current context args value))

(defmethod resolve-query :weather-escape
  [_ context args value]
  (q-weather/escape context args value))

(defmethod resolve-query :available-social-events
  [_ context args value]
  (q-social/available-events context args value))

(defmethod resolve-query :user-profile
  [_ context args value]
  (q-user/profile context args value))

(defmethod resolve-query :experience-history
  [_ context args value]
  (q-experience/history context args value))

(defmethod resolve-mutation :suggestion-accept
  [_ db context args value]
  (mut-suggestion/accept db context args value))

(defmethod resolve-mutation :suggestion-regenerate
  [_ db context args value]
  (mut-suggestion/regenerate db context args value))

(defmethod resolve-mutation :social-event-join
  [_ db context args value]
  (mut-social/join-event db context args value))

(defmethod resolve-mutation :experience-complete
  [_ db context args value]
  (mut-experience/complete db context args value))

(defmethod resolve-mutation :user-signup
  [_ db context args value]
  (mut-auth/signup db context args value))

(defmethod resolve-mutation :user-auth-request
  [_ db context args value]
  (mut-auth/auth-request db context args value))

(defmethod resolve-mutation :user-auth-verify
  [_ db context args value]
  (mut-auth/auth-verify db context args value))

(defmethod resolve-mutation :user-update-profile
  [_ db context args value]
  (mut-auth/update-profile db context args value))

(defmethod resolve-mutation :social-event-create
  [_ db context args value]
  (mut-social/create-event db context args value))

(defn resolver-map
  [db]
  {:query/current-suggestion       (partial resolve-query :current-suggestion)
   :query/weather-escape           (partial resolve-query :weather-escape)
   :query/available-social-events  (partial resolve-query :available-social-events)
   :query/user-profile             (partial resolve-query :user-profile)
   :query/experience-history       (partial resolve-query :experience-history)
   :mutation/suggestion-accept     (partial resolve-mutation :suggestion-accept db)
   :mutation/suggestion-regenerate (partial resolve-mutation :suggestion-regenerate db)
   :mutation/social-event-join     (partial resolve-mutation :social-event-join db)
   :mutation/experience-complete   (partial resolve-mutation :experience-complete db)
   :mutation/user-signup           (partial resolve-mutation :user-signup db)
   :mutation/user-auth-request     (partial resolve-mutation :user-auth-request db)
   :mutation/user-auth-verify      (partial resolve-mutation :user-auth-verify db)
   :mutation/user-update-profile   (partial resolve-mutation :user-update-profile db)
   :mutation/social-event-create   (partial resolve-mutation :social-event-create db)})
