(ns nextplace.resolvers.mutations.social
  (:require [nextplace.db :as db]))

(defn- user-can-join-event?
  "Check if user can join an event based on gender restrictions."
  [user-gender event-restriction]
  (case event-restriction
    :ALL_WELCOME true
    :MEN_ONLY (= user-gender :MAN)
    :WOMEN_ONLY (= user-gender :WOMAN)
    false))

(defn join-event
  "Join a social event. Validates gender restrictions before allowing join."
  [db context args value]
  (let [event-id (:event_id args)
        ;; TODO: Get current user from context/session
        user     {:id "temp" :gender nil}
        ;; TODO: Get event from database
        event    {:gender_restriction :ALL_WELCOME}]
    (if (user-can-join-event? (:gender user) (:gender_restriction event))
      ;; TODO: Add user to event participants
      event
      ;; Return error if gender restriction violated
      (throw (ex-info "You are not eligible to join this event"
                      {:type     :gender-restriction
                       :event-id event-id})))))

(defn create-event
  "Create a new social event from a suggestion.
   Default gender restriction is ALL_WELCOME."
  [db context args value]
  (let [suggestion-id      (:suggestion_id args)
        max-participants   (or (:max_participants args) 4)
        gender-restriction (or (:gender_restriction args) :ALL_WELCOME)
        ;; TODO: Get current user from context/session
        user               {:id "temp"}
        ;; TODO: Get suggestion from database
        ;; TODO: Create event in database
        ]
    ;; Validate max participants
    (when (or (< max-participants 2) (> max-participants 6))
      (throw (ex-info "Max participants must be between 2 and 6"
                      {:type  :validation
                       :field :max_participants})))
    ;; TODO: Return created event
    nil))
