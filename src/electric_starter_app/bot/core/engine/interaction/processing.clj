(ns electric-starter-app.bot.core.engine.interaction.processing
  (:require [clojure.tools.logging :as log]
            [electric-starter-app.bot.platform.discord.schema.definition.interaction :as schema.interactions]
            [electric-starter-app.bot.core.engine.wait.matching :as interaction-matching]
            [electric-starter-app.bot.core.engine.flow.execution :as flow-utils]
            [electric-starter-app.bot.core.engine.flow.processing :as flow-utils2]
            [electric-starter-app.bot.core.engine.wait.processing :as wait-state]
            [electric-starter-app.bot.platform.discord.schema.validation.trigger :as trigger-utils]
            [electric-starter-app.bot.util.state :as state]
            [electric-starter-app.bot.platform.discord.tracking.guild :as guild-tracking]
            [electric-starter-app.bot.impl.flows :refer [available-flows]]))

(defn transform-event
  "Transform JDA events into normalized interaction maps using schema definitions.
   Returns nil for unhandled event types."
  [event bot-name]
  (when-let [schema (schema.interactions/get-schema-for-event event)]
    (log/debug "Found schema for event type:" (-> event .getClass .getSimpleName))
    (try
      (reduce-kv
        (fn [acc field-name {:keys [value required transform]}]
          (let [field-value (cond
                              value value
                              (= :bot-name field-name) bot-name
                              transform (transform event)
                              :else nil)]
            (if (and required (nil? field-value))
              (throw (ex-info "Required field missing"
                       {:field field-name
                        :event-type (-> event .getClass .getSimpleName)}))
              (assoc acc field-name field-value))))
        {}
        (:fields schema))
      (catch Exception e
        (log/error e "Failed to transform event"
          {:event-type (-> event .getClass .getSimpleName)
           :bot-name bot-name})
        nil))))

(defn process-interaction [interaction]
  (log/debug "Processing interaction"
    {:type (:interaction-type interaction)
     :guild-id (:guild-id interaction)
     :channel-id (:channel-id interaction)})

  (try
    ;; Handle guild updates - directly call the function, no require/resolve needed
    (guild-tracking/guild-update interaction)

    ;; Process waiting contexts
    (let [waiting-contexts (interaction-matching/find-waiting-contexts interaction)]
      (log/debug "Found waiting contexts"
        {:count (count waiting-contexts)})

      (doseq [[context-id context wait-field] waiting-contexts]
        (log/debug "Checking wait state"
          {:context-id context-id
           :wait-field wait-field
           :flow-id (:flow-id context)
           :is-queue? (get-in context [:waiting-state wait-field :queue?])})

        (try
          (let [wait-state (get-in context [:waiting-state wait-field])]
            (if (:queue? wait-state)
              ;; Handle queue wait state
              (let [updated-context (-> context
                                      (update-in [:waiting-state wait-field]
                                        wait-state/queue-interaction
                                        interaction))]
                (state/update-state! [:flows :contexts context-id] updated-context)
                (flow-utils/process-flow (:flow-id context) context-id))
              ;; Handle regular wait state
              (let [updated-context (wait-state/complete-wait-state context wait-field interaction)]
                (state/update-state! [:flows :contexts context-id] updated-context)
                (flow-utils/process-flow (:flow-id context) context-id))))
          (catch Exception e
            (log/error e "Failed to process waiting context"
              {:context-id context-id
               :flow-id (:flow-id context)})))))

    ;; Check for new flow triggers
    (when-let [[flow-id trigger-data] (trigger-utils/matches-trigger? interaction)]
      (log/debug "Found matching flow trigger"
        {:flow-id flow-id
         :trigger-data trigger-data})

      (when-let [flow (get available-flows flow-id)]
        (try
          (let [context (flow-utils2/create-flow-context
                          flow-id
                          interaction
                          trigger-data)]
            (log/debug "Created new flow context"
              {:flow-id flow-id
               :context-id (:id context)})
            (flow-utils/process-flow flow-id (:id context)))
          (catch Exception e
            (log/error e "Failed to start new flow"
              {:flow-id flow-id
               :trigger-data trigger-data})))))

    (catch Exception e
      (log/error e "Unexpected error processing interaction"
        {:interaction-type (:interaction-type interaction)}))))