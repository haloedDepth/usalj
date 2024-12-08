(ns electric-starter-app.bot.core.engine.action.processing
  (:require [clojure.tools.logging :as log]
            [electric-starter-app.bot.core.schema.validation.expression :as schema.expressions]))

(defn resolve-inputs
  "Resolves action inputs using expression evaluation.
   Enhanced with type validation and expression verification."
  [input-specs ctx]
  (log/debug "Starting input resolution"
    {:input-specs (keys input-specs)
     :ctx-keys (keys ctx)})

  (try
    (let [resolved (reduce-kv
                     (fn [inputs key spec]
                       (try
                         (let [resolved-value (schema.expressions/evaluate-expression spec ctx)]
                           (log/debug "Resolved input"
                             {:key key
                              :value-type (type resolved-value)
                              :has-value (boolean resolved-value)})
                           (if (nil? resolved-value)
                             (do
                               (log/warn "Input resolved to nil"
                                 {:key key
                                  :spec spec})
                               inputs)
                             (assoc inputs key resolved-value)))
                         (catch Exception e
                           (throw (ex-info "Failed to resolve input"
                                    {:input-key key
                                     :spec spec
                                     :error (.getMessage e)}
                                    e)))))
                     {}
                     input-specs)]

      (log/debug "Completed input resolution"
        {:input-count (count input-specs)
         :resolved-count (count resolved)
         :resolved-keys (keys resolved)})

      resolved)

    (catch Exception e
      (log/error e "Input resolution failed")
      (throw (ex-info "Input resolution failed"
               {:input-specs (keys input-specs)
                :error (.getMessage e)}
               e)))))