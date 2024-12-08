(ns electric-starter-app.bot.core.engine.action.execution
  (:require [clojure.tools.logging :as log]
            [electric-starter-app.bot.core.schema.validation.context :as schema.context]
            [electric-starter-app.bot.core.schema.validation.action :refer [validate-action-inputs validate-action-outputs]]
            [electric-starter-app.bot.impl.actions :refer [registered-actions]]))

(defn is-acceptable-error?
  "Determines if an error should allow flow continuation"
  [action-def error-result]
  (when (and (:acceptable-errors action-def)
          (:error error-result))
    (contains? (:acceptable-errors action-def)
      (get-in error-result [:error :type]))))

(defn execute-action
  "Executes an action with validated inputs and returns its outputs.
   Now handles acceptable errors that shouldn't halt flow execution."
  [{:keys [action inputs]} ctx]
  (try
    (when-not (contains? registered-actions action)
      (throw (ex-info "Unknown action type"
               {:action action
                :available-actions (keys registered-actions)})))

    (let [action-def (get registered-actions action)
          handler (:handler action-def)
          validated-ctx (schema.context/validate-context ctx)]

      (log/debug "Execute action called"
        {:action action
         :action-name (:name action-def)
         :description (:description action-def)
         :inputs (keys inputs)})

      (let [validated-inputs (validate-action-inputs action inputs)
            _ (log/debug "Executing action with validated inputs"
                {:action action
                 :validated-inputs (keys validated-inputs)})

            raw-result (try
                         (handler validated-inputs validated-ctx)
                         (catch Exception e
                           (log/error e "Handler execution failed"
                             {:action action
                              :inputs validated-inputs})
                           (throw (ex-info "Action handler failed"
                                    {:action action
                                     :type :handler-execution-error
                                     :cause e}))))

            _ (log/debug "Handler returned result"
                {:action action
                 :result-keys (keys raw-result)})

            _ (when-not (map? raw-result)
                (throw (ex-info "Handler must return a map"
                         {:action action
                          :result raw-result
                          :result-type (type raw-result)})))

            _ (when-not (contains? raw-result :success)
                (throw (ex-info "Handler result must contain :success key"
                         {:action action
                          :result raw-result})))

            validated-outputs (validate-action-outputs action raw-result)]

        (log/debug "Action execution completed"
          {:action action
           :success (:success validated-outputs)
           :has-error (contains? validated-outputs :error)
           :error-type (get-in validated-outputs [:error :type])
           :output-keys (keys validated-outputs)})

        ;; Key change: If the error is acceptable, we treat it as a "successful failure"
        (if (and (not (:success validated-outputs))
              (is-acceptable-error? action-def validated-outputs))
          (do
            (log/info "Action completed with acceptable error"
              {:action action
               :error-type (get-in validated-outputs [:error :type])})
            ;; Return the outputs but mark as successful for flow continuation
            (assoc validated-outputs :success true))
          validated-outputs)))

    (catch Exception e
      (let [error-data {:action action
                        :error-type (or (-> e ex-data :type) :action-execution-error)
                        :details (.getMessage e)
                        :cause e}]
        (log/error e "Failed to execute action" error-data)
        {:success false
         :error error-data}))))