(ns electric-starter-app.bot.core.schema.validation.context
  (:require [clojure.tools.logging :as log] 
            [electric-starter-app.bot.core.schema.definition.context :refer [context-schema]]))

(defn initialize-context
  "Creates a new context map with initial values from schema.
   Merges provided values with schema defaults."
  [initial-values]
  (log/debug "Initializing new context" {:initial-values initial-values})
  (let [context (reduce-kv
                  (fn [ctx k v]
                    (assoc ctx k (or (get initial-values k)
                                   (:initial-value v)
                                   nil)))
                  {}
                  context-schema)]
    (log/debug "Context initialized" {:context context})
    context))

(defn validate-context
  "Validates a context map against the schema.
   Returns context if valid, throws ex-info if invalid."
  [context]
  (log/debug "Validating context" {:context-id (:id context)})

  ;; Validate required fields and their types
  (doseq [[k v] context-schema
          :when (:required v)]
    (when (nil? (get context k))
      (throw (ex-info "Missing required context field"
               {:field k
                :context-id (:id context)})))

    (when-let [validate (:validation v)]
      (when-not (validate (get context k))
        (throw (ex-info "Invalid context field value"
                 {:field k
                  :value (get context k)
                  :context-id (:id context)})))))
  context)