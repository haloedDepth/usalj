(ns electric-starter-app.bot.core.schema.definition.wait-state
  (:require [clojure.tools.logging :as log]
            [electric-starter-app.bot.platform.discord.schema.definition.interaction :refer [interaction-schemas]]))

(def waiting-state-schema
  "Schema for individual waiting state entries"
  {:interaction-type {:type :keyword
                      :required true
                      :validation #(contains? interaction-schemas %)
                      :doc "Type of interaction being waited for"}

   :input-filters {:type :map
                   :required false
                   :doc "Conditions that must be met by the incoming interaction"}

   :output-filters {:type :vector
                    :required false
                    :doc "Vector of maps containing target step index and conditions"}

   :interaction {:type :map
                 :required false
                 :validation #(when % (contains? interaction-schemas (:interaction-type %)))
                 :doc "The received interaction that satisfied this wait state, nil if still waiting"}

   :queue? {:type :boolean
            :required false
            :default false
            :doc "Whether this wait state should queue matching interactions"}

   :queue {:type :vector
           :required false
           :initial-value []
           :doc "Queue of pending interactions"}})

(defn create-wait-state
  "Creates a new waiting state entry with validated and resolved filters"
  [interaction-type & {:keys [input-filters output-filters queue?]}]
  (log/debug "Creating wait state entry"
    {:interaction-type interaction-type
     :input-filters input-filters
     :output-filters output-filters
     :queue? queue?})

  {:interaction-type interaction-type
   :input-filters (or input-filters {})
   :output-filters (or output-filters [])
   :interaction nil
   :queue? (boolean queue?)
   :queue (when queue? [])})

(defn complete-wait-state
  "Returns an updated wait state entry with the received interaction."
  [wait-state interaction]
  (log/debug "Completing wait state" {:interaction-type (:interaction-type wait-state)})
  (assoc wait-state :interaction interaction))