(ns electric-starter-app.bot.util.state
  (:require [clojure.tools.logging :as log]
            [electric-starter-app.bot.util.config :as config]))

(def state (atom nil))

(defn update-state!
  "Updates a specific path in the state atom with a new value.
   state-path - vector of keywords representing the path to update
   value - new value to set at the path
   Returns the updated state."
  [state-path value]
  (log/debug "Updating state at path" state-path)
  (try
    (swap! state update-in state-path (constantly value))
    (log/debug "State updated successfully at path" state-path "with value:" value)
    @state
    (catch Exception e
      (log/error e "Failed to update state at path" state-path)
      (throw (ex-info "State update failed"
               {:path state-path
                :value value
                :error (.getMessage e)}
               e)))))

;; Initialize state on namespace load
(log/info "Initializing application state")
(def initial-state
  (merge (config/get-config)
    {:flows {:registry {}    ; Flow definitions will be stored here
             :contexts {}}})) ; Flow execution contexts will be stored here

(reset! state initial-state)
(log/info "Application state initialized successfully")