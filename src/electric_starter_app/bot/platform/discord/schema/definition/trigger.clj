(ns electric-starter-app.bot.platform.discord.schema.definition.trigger
  (:require [clojure.tools.logging :as log]))

(def command-option-schema
  {:name {:type :string
          :required true
          :doc "Name of the option"}
   :description {:type :string
                 :required true
                 :doc "Description of the option"}
   :type {:type :keyword
          :required true
          :doc "Type of the option: string, integer, boolean, user, channel, role"}
   :required {:type :boolean
              :required true
              :doc "Whether this option is required"}})

;; REPLACED: Old trigger-schema with new slash-command focused schema
(def trigger-schema
  {:name {:type :keyword
          :required true
          :doc "Unique identifier for the trigger"}
   :command-name {:type :string
                  :required true
                  :doc "Slash command name (what users will type after /)"}
   :description {:type :string
                 :required true
                 :doc "Description of the command - shown in Discord UI"}
   :options {:type :vector
             :required false
             :validation #(every? (fn [opt] (every? (fn [[k v]]
                                                      (contains? command-option-schema k))
                                              opt))
                            %)
             :doc "Vector of command options following Discord's slash command structure"}
   :flow-id {:type :keyword
             :required true
             :doc "ID of flow to trigger"}})

(def valid-option-types
  #{:string :integer :boolean :user :channel :role})

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
