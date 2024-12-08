(ns electric-starter-app.bot.platform.discord.schema.validation.interaction
  (:require [clojure.tools.logging :as log]
            [electric-starter-app.bot.platform.discord.schema.definition.interaction :as schema.interactions]))

(defn validate-interaction-type
  "Validates that an interaction type exists in the schema.
   Throws detailed exception for invalid types."
  [interaction-type]
  (log/debug "Validating interaction type" {:type interaction-type})
  (when (and interaction-type
          (not (contains? schema.interactions/interaction-schemas interaction-type)))
    (throw (ex-info "Invalid interaction type"
             {:type interaction-type
              :valid-types (keys schema.interactions/interaction-schemas)}))))

(defn validate-interaction-fields
  "Validates that an interaction map matches its schema definition.
   Checks required fields and field types."
  [interaction-type interaction-map]
  (log/debug "Validating interaction fields"
    {:type interaction-type :fields (keys interaction-map)})
  (when (and interaction-type interaction-map)
    (let [schema-def (get schema.interactions/interaction-schemas interaction-type)
          required-fields (->> schema-def
                            :fields
                            (filter (fn [[_ v]] (:required v)))
                            (map first)
                            set)]
      (doseq [field required-fields]
        (when-not (contains? interaction-map field)
          (throw (ex-info "Missing required interaction field"
                   {:interaction-type interaction-type
                    :missing-field field
                    :required-fields required-fields})))))))

(defn validate-interaction
  "Validates a complete interaction against its schema definition.
   Checks both type and required fields."
  [interaction]
  (log/debug "Validating interaction"
    {:type (:interaction-type interaction)})
  (when interaction
    (validate-interaction-type (:interaction-type interaction))
    (validate-interaction-fields (:interaction-type interaction) interaction)
    interaction))