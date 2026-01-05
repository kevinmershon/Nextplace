(ns nextplace.resolvers.queries.social
  (:require [nextplace.db :as db]))

(defn- user-can-see-event?
  "Check if user can see an event based on gender restrictions.
   Users cannot see events they are not welcome to attend."
  [user-gender event-restriction]
  (case event-restriction
    :ALL_WELCOME true
    :MEN_ONLY (= user-gender :MAN)
    :WOMEN_ONLY (= user-gender :WOMAN)
    ;; Default to not showing restricted events if gender unknown
    true))

(defn- filter-events-by-gender
  "Filter events to only those the user can see based on their gender."
  [user events]
  (let [user-gender (:gender user)]
    (filter #(user-can-see-event? user-gender (:gender_restriction %)) events)))

(defn available-events
  "Get available social events filtered by user's gender.
   Users only see events they are welcome to attend."
  [context args value]
  ;; TODO: Get current user from context/session
  ;; TODO: Get events from database
  ;; TODO: Filter by gender
  (let [;; Placeholder - get user from context when auth is wired up
        user   {:gender nil}
        ;; Placeholder - get events from db
        events []]
    (filter-events-by-gender user events)))
