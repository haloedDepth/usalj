(ns electric-starter-app.bot.core.engine.flow.processing
  (:require [clojure.tools.logging :as log]
            [electric-starter-app.bot.platform.discord.schema.validation.interaction :as schema.interaction]
            [electric-starter-app.bot.core.schema.validation.context :as schema.context]
            [electric-starter-app.bot.util.state :as state]
            [electric-starter-app.bot.impl.flows :refer [available-flows]]))

(defn extract-context-data
  [flow interaction]
  (let [interaction-type (:interaction-type interaction)
        trigger-data (:trigger-data interaction)]
    (schema.interaction/validate-interaction-type interaction-type)
    (schema.interaction/validate-interaction-fields interaction-type interaction)

    ;; Merge different sources of context data
    (merge
      ;; Extract from interaction
      (reduce-kv
        (fn [ctx key schema]
          (case (:source schema)
            :interaction (assoc ctx key (get-in interaction (:path schema)))
            :trigger (assoc ctx key (get-in (:trigger-data interaction) (:path schema)))
            :bot-state (let [path (replace {:bot-name (:bot-name interaction)}
                                    (:path schema))]
                         (assoc ctx key (get-in @state/state path)))))
        {}
        (:context-schema flow))

      ;; Now handle options from slash commands instead of trigger arguments
      (when (= :slash-command (:interaction-type interaction))
        (:options interaction)))))

(defn create-flow-context
  [flow-id interaction trigger-data]
  (let [flow (get available-flows flow-id)
        interaction-type (:interaction-type interaction)]
    (schema.interaction/validate-interaction-type interaction-type)
    (schema.interaction/validate-interaction-fields interaction-type interaction)

    (let [interaction-with-trigger (assoc interaction :trigger-data trigger-data)
          initial-context (schema.context/initialize-context
                            {:id (random-uuid)
                             :flow-id flow-id
                             :current-step-index 0
                             :interaction interaction-with-trigger})
          context (merge initial-context
                    (extract-context-data flow interaction-with-trigger))
          validated-context (schema.context/validate-context context)]

      (state/update-state! [:flows :contexts (:id validated-context)]
        validated-context)

      (log/debug "Created new flow context"
        {:context-id (:id validated-context)
         :flow-id flow-id
         :has-trigger-data? (boolean trigger-data)})

      validated-context)))