(ns electric-starter-app.bot.core.schema.definition.context
  (:require [electric-starter-app.bot.platform.discord.schema.definition.interaction :refer [interaction-schemas]]
            [electric-starter-app.bot.core.schema.validation.wait-state :as wait-state]))

(def context-schema
  "Schema defining the structure and initial values for flow contexts."
  {:id {:type :uuid
        :required true
        :doc "Unique identifier for the context"}

   :flow-id {:type :keyword
             :required true
             :doc "ID of the flow being executed"}

   :current-step-index {:type :integer
                        :required true
                        :initial-value 0
                        :doc "Current position in flow steps sequence"}

   :interaction {:type :map
                 :required true
                 :validation #(contains? interaction-schemas (:interaction-type %))
                 :doc "Original interaction that triggered the flow"}

   :action-outputs {:type :map
                    :required true
                    :initial-value {}
                    :doc "Accumulated outputs from executed actions (excluding waiting states)"}

   :waiting-state {:type :map
                   :required true
                   :initial-value {}
                   :validation wait-state/validate-waiting-states
                   :doc "Map of named wait states to their current status"}

   :error {:type :map
           :required false
           :doc "Error information if flow execution failed"}})