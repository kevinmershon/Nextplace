(ns nextplace.resolvers
  (:require [nextplace.db :as db])
  (:import [java.time Instant]
           [java.util UUID]))

(defmulti resolve-query
  (fn [field-name context args value] field-name))

(defmulti resolve-mutation
  (fn [field-name db context args value] field-name))

(defmethod resolve-query :current-suggestion
  [_ context args value]
  nil)

(defmethod resolve-query :weather-escape
  [_ context args value]
  nil)

(defmethod resolve-query :available-social-events
  [_ context args value]
  [])

(defmethod resolve-query :user-profile
  [_ context args value]
  nil)

(defmethod resolve-query :experience-history
  [_ context args value]
  [])

(defmethod resolve-mutation :suggestion-accept
  [_ db context args value]
  nil)

(defmethod resolve-mutation :suggestion-regenerate
  [_ db context args value]
  nil)

(defmethod resolve-mutation :social-event-join
  [_ db context args value]
  nil)

(defmethod resolve-mutation :experience-complete
  [_ db context args value]
  nil)

(defmethod resolve-mutation :user-signup
  [_ db context args value]
  (let [email        (:email args)
        user-id      (str (UUID/randomUUID))
        signed-up-at (str (Instant/now))
        user-data    {:id         user-id
                      :email      email
                      :signedUpAt signed-up-at}]
    (db/put-value db (str "user:" email) user-data)
    user-data))

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
   :mutation/user-signup           (partial resolve-mutation :user-signup db)})
