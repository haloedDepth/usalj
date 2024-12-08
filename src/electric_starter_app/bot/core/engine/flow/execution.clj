(ns electric-starter-app.bot.core.engine.flow.execution
  (:require [clojure.tools.logging :as log]
            [electric-starter-app.bot.core.schema.validation.context :as schema.context]
            [electric-starter-app.bot.util.state :as state]
            [electric-starter-app.bot.core.engine.wait.processing :as wait-state]
            [electric-starter-app.bot.impl.flows :refer [available-flows]]
            [electric-starter-app.bot.core.schema.validation.action :refer [validate-action-spec]]
            [electric-starter-app.bot.impl.actions :refer [registered-actions]]
            [electric-starter-app.bot.core.engine.action.processing :as action.processing]
            [electric-starter-app.bot.core.engine.action.execution :as action.execution]))

(defn process-action
  "Process a single action within a flow.
   Uses new action registry and enhanced validation system.
   Returns updated context with action outputs and incremented step index."
  [context action-spec]
  (log/debug "Starting process-action"
    {:context-id (:id context)
     :flow-id (:flow-id context)
     :action (:action action-spec)
     :current-step (:current-step-index context)})

  (try
    (let [;; Initial context validation
          validated-context (schema.context/validate-context context)

          ;; Validate action specification
          _ (log/debug "Validating action spec"
              {:action (:action action-spec)
               :has-inputs (boolean (:inputs action-spec))
               :has-outputs (boolean (:outputs action-spec))})

          validated-spec (validate-action-spec action-spec)

          ;; Get action definition for enhanced logging
          action-def (get registered-actions (:action validated-spec))

          _ (log/debug "Processing action"
              {:action (:action validated-spec)
               :description (:description action-def)
               :step-index (:current-step-index validated-context)})

          ;; Resolve and validate inputs
          resolved-inputs (action.processing/resolve-inputs (:inputs validated-spec) validated-context)

          ;; Execute action with resolved inputs
          action-result (action.execution/execute-action
                          {:action (:action validated-spec)
                           :inputs resolved-inputs}
                          validated-context)]

      (if (:success action-result)
        (let [;; Map outputs to context keys
              mapped-outputs (reduce-kv
                               (fn [acc from-key to-key]
                                 (if-let [value (get action-result from-key)]
                                   (assoc acc to-key value)
                                   acc))
                               {}
                               (:outputs validated-spec))

              ;; Create updated context
              updated-context (-> validated-context
                                (update :action-outputs merge mapped-outputs)
                                (update :current-step-index inc))

              ;; Clear queue interaction if needed
              final-context (if-let [[wait-field wait-state]
                                     (first (filter (fn [[_ v]]
                                                      (:queue? v))
                                              (:waiting-state updated-context)))]
                              (assoc-in updated-context [:waiting-state wait-field :interaction] nil)
                              updated-context)]

          (log/debug "Action processing completed successfully"
            {:context-id (:id validated-context)
             :flow-id (:flow-id validated-context)
             :action (:action validated-spec)
             :step-index (:current-step-index validated-context)
             :mapped-outputs (keys mapped-outputs)})

          ;; Validate and return updated context
          (schema.context/validate-context final-context))

        ;; Handle action failure
        (let [error-context (assoc validated-context
                              :error (:error action-result)
                              :failed-step (:current-step-index validated-context)
                              :failed-action (:action validated-spec))]
          (log/error "Action execution failed"
            {:context-id (:id validated-context)
             :flow-id (:flow-id validated-context)
             :action (:action validated-spec)
             :step-index (:current-step-index validated-context)
             :error (:error action-result)
             :description (:description action-def)})

          (schema.context/validate-context error-context))))

    (catch Exception e
      (log/error e "Unexpected error in process-action"
        {:context-id (:id context)
         :flow-id (:flow-id context)
         :action (:action action-spec)
         :step-index (:current-step-index context)})

      (schema.context/validate-context
        (assoc context
          :error {:type :process-action-error
                  :action (:action action-spec)
                  :details (.getMessage e)
                  :cause e}
          :failed-step (:current-step-index context)
          :failed-action (:action action-spec))))))

(defn process-flow
  [flow-id context-id]
  (let [flow (get available-flows flow-id)]
    (loop [iteration 0]
      (when (> iteration 1000)
        (throw (ex-info "Flow iteration limit exceeded"
                 {:flow-id flow-id
                  :context-id context-id
                  :iterations iteration})))

      (let [context (get-in @state/state [:flows :contexts context-id])
            _ (when-not context
                (throw (ex-info "Context not found"
                         {:context-id context-id})))

            current-step (get-in flow [:steps (:current-step-index context)])

            _ (log/debug "Flow processing state"
                {:context-id context-id
                 :flow-id flow-id
                 :iteration iteration
                 :step-index (:current-step-index context)
                 :step-type (cond
                              (:wait current-step) :wait
                              (:action current-step) :action
                              :else nil)
                 :has-error (:error context)
                 :waiting-states (count (:waiting-state context))})]

        (cond
          (:error context)
          (do
            (log/error "Flow execution stopped due to error"
              {:context-id context-id
               :flow-id flow-id
               :error (:error context)})
            (state/update-state! [:flows :contexts context-id] nil)
            context)

          ;; Check for queued interactions to process
          (seq (wait-state/get-queue-wait-states (:waiting-state context)))
          (let [queue-states (wait-state/get-queue-wait-states (:waiting-state context))]
            (if-let [[wait-field wait-state] (first (filter (fn [[_ v]]
                                                              (seq (:queue v)))
                                                      queue-states))]
              (if-let [updated-context (wait-state/process-next-queued context wait-field)]
                (do
                  (state/update-state! [:flows :contexts context-id] updated-context)
                  (let [next-step (get-in flow [:steps (:current-step-index updated-context)])]
                    (if (:action next-step)
                      ;; If next step is an action, process it immediately
                      (let [action-result (process-action updated-context next-step)]
                        (state/update-state! [:flows :contexts context-id] action-result)
                        (recur (inc iteration)))
                      ;; Otherwise continue normal flow
                      (recur (inc iteration)))))
                context)
              context))

          (wait-state/get-active-wait-state (:waiting-state context))
          (do
            (log/info "Flow paused - waiting for interaction"
              {:context-id context-id
               :flow-id flow-id
               :step-index (:current-step-index context)})
            context)

          (nil? current-step)
          (let [has-queue-wait? (boolean (seq (wait-state/get-queue-wait-states
                                                (:waiting-state context))))]
            (if has-queue-wait?
              ;; If we have a queue wait state, find its step index and reset
              (let [wait-step-index (first
                                      (keep-indexed
                                        (fn [idx step]
                                          (when (:wait step) idx))
                                        (:steps flow)))
                    reset-context (-> context
                                    (assoc :current-step-index wait-step-index))]
                (state/update-state! [:flows :contexts context-id] reset-context)
                (recur (inc iteration)))
              ;; No queue wait state, normal completion
              (do
                (log/info "Flow completed successfully - purging context"
                  {:context-id context-id
                   :flow-id flow-id})
                (state/update-state! [:flows :contexts context-id] nil)
                nil)))

          :else
          (let [updated-context
                (try
                  (cond
                    (:wait current-step)
                    (wait-state/process-wait-step context (:wait current-step))

                    (:action current-step)
                    (process-action context current-step)

                    :else
                    (throw (ex-info "Invalid flow step type"
                             {:step current-step
                              :step-index (:current-step-index context)})))
                  (catch Exception e
                    (let [error-context (-> context
                                          (assoc :error {:type :step-processing-error
                                                         :step-type (cond
                                                                      (:wait current-step) :wait
                                                                      (:action current-step) :action
                                                                      :else :unknown)
                                                         :details (.getMessage e)
                                                         :cause e}
                                            :failed-step (:current-step-index context))
                                          (schema.context/validate-context))]
                      (log/error e "Step processing failed"
                        {:context-id context-id
                         :flow-id flow-id})
                      error-context)))]

            (state/update-state! [:flows :contexts context-id]
              updated-context)
            (recur (inc iteration))))))))