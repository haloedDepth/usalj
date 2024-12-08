(ns electric-starter-app.bot.platform.discord.schema.validation.trigger
  (:require [clojure.tools.logging :as log]
            [electric-starter-app.bot.impl.triggers :as triggers]
            [electric-starter-app.bot.platform.discord.schema.definition.trigger :refer [valid-option-types command-option-schema]]))

(defn validate-option-type
  "Validates that an option type is supported by Discord"
  [type]
  (when-not (valid-option-types type)
    (throw (ex-info "Invalid option type"
             {:type type
              :valid-types valid-option-types}))))

(defn create-command-option
  "Creates a validated command option map"
  [{:keys [name description type required] :as option}]
  (validate-option-type type)
  (when-not (string? name)
    (throw (ex-info "Option name must be a string"
             {:option option})))
  (when-not (string? description)
    (throw (ex-info "Option description must be a string"
             {:option option})))
  (when (> (count description) 100)
    (throw (ex-info "Option description must be 100 characters or less"
             {:option option
              :description description})))
  {:name name
   :description description
   :type type
   :required (boolean required)})

;; MODIFIED: Updated to handle slash command structure
(defn create-trigger
  "Creates a new validated trigger definition for slash commands"
  [{:keys [name command-name description options flow-id] :as trigger-def}]
  (log/debug "Creating slash command trigger definition"
    {:name name :command-name command-name :flow-id flow-id})

  (when-not (re-matches #"^[\w-]{1,32}$" command-name)
    (throw (ex-info "Invalid command name format. Must be 1-32 characters, alphanumeric or dash"
             {:command-name command-name})))

  (when-not (string? description)
    (throw (ex-info "Description must be a string"
             {:description description})))

  (when (> (count description) 100)
    (throw (ex-info "Description must be 100 characters or less"
             {:description description})))

  (when options
    (doseq [option options]
      (when-not (every? #(contains? command-option-schema (first %)) option)
        (throw (ex-info "Invalid option format"
                 {:option option
                  :valid-keys (keys command-option-schema)})))))

  trigger-def)

;; NEW: Function to convert trigger to Discord command data
(defn trigger->command-data
  "Converts a trigger definition to Discord command creation data"
  [{:keys [command-name description options]}]
  {:name command-name
   :description description
   :options (when options
              (mapv (fn [{:keys [name description type required]}]
                      {:name name
                       :description description
                       :type type
                       :required required})
                options))})

(defn extract-trigger-data
  "Extracts trigger data from slash command options"
  [command-name options trigger-def]
  (log/debug "Extracting trigger data from slash command"
    {:command command-name :options options})

  ;; For slash commands, we create an arguments map directly from the options
  (let [args (when options
               (reduce-kv
                 (fn [m k v]
                   (assoc m (keyword k) v))
                 {}
                 options))]

    {:arguments args
     :trigger-name (:name trigger-def)
     :flow-id (:flow-id trigger-def)}))

(defn matches-trigger?
  "Checks if an interaction matches any registered commands.
   Returns [flow-id trigger-data] if matched, nil otherwise."
  [interaction]
  (when (= :slash-command (:interaction-type interaction))
    (let [command-name (:command-name interaction)
          options (:options interaction)]

      (log/debug "Checking slash command against triggers"
        {:command command-name})

      (when-let [trigger-def (get triggers/trigger-registry command-name)]
        (log/debug "Found matching command trigger"
          {:command command-name
           :trigger-name (:name trigger-def)})

        (when-let [trigger-data (extract-trigger-data
                                  command-name
                                  options
                                  trigger-def)]
          [(:flow-id trigger-def) trigger-data])))))

