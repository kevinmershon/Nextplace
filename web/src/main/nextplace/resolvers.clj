(ns nextplace.resolvers)

(defmulti resolve-query
  (fn [field-name context args value] field-name))

(defmulti resolve-mutation
  (fn [field-name context args value] field-name))

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

(defmethod resolve-mutation :accept-suggestion
  [_ context args value]
  nil)

(defmethod resolve-mutation :regenerate-suggestion
  [_ context args value]
  nil)

(defmethod resolve-mutation :join-social-event
  [_ context args value]
  nil)

(defmethod resolve-mutation :complete-experience
  [_ context args value]
  nil)

(defn resolver-map
  []
  {:query/current-suggestion       (partial resolve-query :current-suggestion)
   :query/weather-escape           (partial resolve-query :weather-escape)
   :query/available-social-events  (partial resolve-query :available-social-events)
   :query/user-profile             (partial resolve-query :user-profile)
   :query/experience-history       (partial resolve-query :experience-history)
   :mutation/accept-suggestion     (partial resolve-mutation :accept-suggestion)
   :mutation/regenerate-suggestion (partial resolve-mutation :regenerate-suggestion)
   :mutation/join-social-event     (partial resolve-mutation :join-social-event)
   :mutation/complete-experience   (partial resolve-mutation :complete-experience)})
