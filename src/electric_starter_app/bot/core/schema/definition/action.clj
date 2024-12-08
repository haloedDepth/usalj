(ns electric-starter-app.bot.core.schema.definition.action)

(def field-types
  #{:string :integer :boolean :map :vector :jda-client :function :keyword :any})

(def action-schema
  {:name {:type :keyword
          :required true
          :doc "Unique identifier for the action"}
   :description {:type :string
                 :required true
                 :doc "Human readable description of the action"}
   :acceptable-errors {:type :set
                       :required false
                       :doc "Set of error types that should not halt flow execution"}
   :spec {:type :map
          :required true
          :doc "Input and output specifications"}
   :handler {:type :function
             :required true
             :doc "Function that executes the action"}})