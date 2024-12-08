(ns electric-starter-app.bot.core.schema.validation.action
  (:require [clojure.tools.logging :as log]
            [electric-starter-app.bot.core.schema.definition.expression :as schema.expressions]
            [electric-starter-app.bot.platform.discord.schema.definition.interaction :as schema.interactions]
            [electric-starter-app.bot.core.schema.validation.expression :as validation.expressions]
            [electric-starter-app.bot.core.schema.definition.action :refer [field-types action-schema]]
            [electric-starter-app.bot.impl.actions :refer [registered-actions]]))

(defn validate-field-definition
  "Validates a single field definition"
  [field-def]
  (and (map? field-def)
    (contains? field-types (:type field-def))
    (string? (:doc field-def))))

(defn validate-fields-map
  "Validates a map of field definitions"
  [fields]
  (and (map? fields)
    (every? (fn [[k v]]
              (and (keyword? k)
                (validate-field-definition v)))
      fields)))

(defn validate-io-spec
  "Validates input/output specification"
  [spec]
  (and (map? spec)
    (validate-fields-map spec)))

(defn validate-required-fields
  "Validates presence of required fields"
  [action schema]
  (every? #(contains? action %)
    (map first (filter #(:required (second %)) schema))))

(defn validate-action-spec
  "Validates action spec including expressions in inputs and outputs.
   Uses new action registry and schema validation."
  [action-spec]
  (let [{:keys [action inputs outputs]} action-spec]
    (log/debug "Validating action spec"
      {:action action
       :has-inputs (boolean inputs)
       :has-outputs (boolean outputs)})

    ;; Validate action exists in registry
    (when-not (contains? registered-actions action)
      (throw (ex-info "Unknown action type"
               {:action action
                :available-actions (keys registered-actions)})))

    (let [action-def (get registered-actions action)
          spec-inputs (get-in action-def [:spec :inputs])
          spec-outputs (get-in action-def [:spec :outputs])]

      ;; Validate inputs and their expressions
      (when inputs
        (doseq [[input-key input-value] inputs]
          (when-not (contains? spec-inputs input-key)
            (throw (ex-info "Invalid input for action"
                     {:action action
                      :invalid-input input-key
                      :valid-inputs (keys spec-inputs)
                      :action-description (:description action-def)})))

          ;; Validate expression
          (when (map? input-value)
            (try
              (validation.expressions/validate-expression input-value)
              (catch Exception e
                (throw (ex-info "Invalid expression in action input"
                         {:action action
                          :input input-key
                          :expression input-value
                          :error (.getMessage e)})))))))

      ;; Handle special case for wait-for-interaction
      ;; Justification: This is core flow functionality that needs to be maintained
      ;; even with new action system
      (when (= action :wait-for-interaction)
        (let [interaction-type (when-let [type-expr (get inputs :interaction-type)]
                                 (if (map? type-expr)
                                   (validation.expressions/evaluate-expression type-expr {})
                                   type-expr))]

          (when-not (contains? schema.interactions/interaction-schemas interaction-type)
            (throw (ex-info "Invalid interaction type in wait action"
                     {:action action
                      :invalid-type interaction-type
                      :valid-types (keys schema.interactions/interaction-schemas)})))

          (when-not (and outputs
                      (contains? outputs :waiting-state)
                      (keyword? (:waiting-state outputs)))
            (throw (ex-info "wait-for-interaction must map :waiting-state output to a keyword"
                     {:action action
                      :outputs outputs})))))

      ;; Validate outputs based on action type
      (when outputs
        (if (= action :wait-for-interaction)
          ;; Special case validation
          (do
            (when-not (contains? outputs :waiting-state)
              (throw (ex-info "Missing :waiting-state output mapping for wait-for-interaction"
                       {:action action
                        :outputs outputs})))
            (when-not (keyword? (:waiting-state outputs))
              (throw (ex-info "wait-for-interaction :waiting-state output must be mapped to a keyword"
                       {:action action
                        :waiting-state (:waiting-state outputs)
                        :type (type (:waiting-state outputs))}))))

          ;; Standard action output validation
          (doseq [[output-key output-target] outputs]
            (when-not (contains? spec-outputs output-key)
              (throw (ex-info "Invalid output mapping for action"
                       {:action action
                        :invalid-output output-key
                        :valid-outputs (keys spec-outputs)
                        :action-description (:description action-def)})))

            (when-not (keyword? output-target)
              (throw (ex-info "Output must be mapped to a keyword"
                       {:action action
                        :output output-key
                        :target output-target}))))))

      (log/debug "Action spec validation completed"
        {:action action
         :valid true})

      action-spec)))

(defn validate-field-types
  "Validates types of fields"
  [action schema]
  (every? (fn [[field spec]]
            (case (:type spec)
              :keyword (keyword? (get action field))
              :string (string? (get action field))
              :map (map? (get action field))
              :function (fn? (get action field))
              :set (or (nil? (get action field))  ; Optional field
                     (set? (get action field)))
              true))
    schema))

(defn validate-action-definition
  "Validates an action definition against the schema"
  [action]
  (log/debug "Validating action" {:action-name (:name action)})
  (try
    (when-not (validate-required-fields action action-schema)
      (throw (ex-info "Missing required fields"
               {:action action
                :schema action-schema})))

    (when-not (validate-field-types action action-schema)
      (throw (ex-info "Invalid field types"
               {:action action
                :schema action-schema})))

    (when-not (validate-action-spec (:spec action))
      (throw (ex-info "Invalid action specification"
               {:action-name (:name action)
                :spec (:spec action)})))

    true
    (catch Exception e
      (log/error e "Action validation failed" {:action action})
      false)))



(defn validate-action-inputs
  "Validates action inputs against their spec and expression schema.
   Checks required inputs, types, and expression structure."
  [action-type inputs]
  (log/debug "Validating action inputs"
    {:action action-type :inputs (keys inputs)})

  (when-not (contains? registered-actions action-type)
    (throw (ex-info "Unknown action type"
             {:action action-type
              :available-actions (keys registered-actions)})))

  (let [action-def (get registered-actions action-type)
        input-spec (get-in action-def [:spec :inputs])]

    ;; Validate required inputs
    (doseq [[input-key {:keys [required type doc validation]}] input-spec
            :when required]
      (when-not (contains? inputs input-key)
        (throw (ex-info "Missing required input"
                 {:action action-type
                  :missing-input input-key
                  :input-doc doc
                  :expected-type type})))

      ;; Validate type and custom validation if present
      (when-let [input-value (get inputs input-key)]
        (when validation
          (when-not (validation input-value)
            (throw (ex-info "Input validation failed"
                     {:action action-type
                      :input input-key
                      :value input-value
                      :type type}))))))

    ;; Validate input value expressions
    (doseq [[input-key input-value] inputs]
      (when (and (map? input-value) (:type input-value))
        (try
          (validation.expressions/validate-expression input-value)
          (catch Exception e
            (throw (ex-info "Invalid input expression"
                     {:action action-type
                      :input input-key
                      :expression input-value
                      :error (.getMessage e)}))))))
    inputs))

(defn validate-action-outputs
  "Validates action outputs against their spec.
   Handles both required outputs and type validation."
  [action-type outputs]
  (log/debug "Validating action outputs"
    {:action action-type :outputs (keys outputs)})

  (when-not (contains? registered-actions action-type)
    (throw (ex-info "Unknown action type"
             {:action action-type
              :available-actions (keys registered-actions)})))

  (let [action-def (get registered-actions action-type)
        output-spec (get-in action-def [:spec :outputs])]

    ;; Validate required outputs and their types
    (doseq [[output-key {:keys [required type validation doc]}] output-spec]
      ;; Check required outputs
      (when (and required (not (contains? outputs output-key)))
        (throw (ex-info "Missing required output"
                 {:action action-type
                  :missing-output output-key
                  :output-doc doc})))

      ;; Validate type and custom validation if value exists
      (when-let [output-value (get outputs output-key)]
        (when validation
          (when-not (validation output-value)
            (throw (ex-info "Output validation failed"
                     {:action action-type
                      :output output-key
                      :value output-value
                      :type type
                      :doc doc}))))))

    ;; Check for unexpected outputs
    (doseq [output-key (keys outputs)]
      (when-not (contains? output-spec output-key)
        (log/warn "Unexpected output from action"
          {:action action-type
           :unexpected-output output-key
           :valid-outputs (keys output-spec)})))

    outputs))