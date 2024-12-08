(ns electric-starter-app.bot.core.engine.wait.matching
  (:require [clojure.tools.logging :as log]
            [clojure.string :as str]
            [electric-starter-app.bot.platform.discord.schema.definition.interaction :as schema.interactions]
            [electric-starter-app.bot.core.schema.validation.context :as schema.context]
            [electric-starter-app.bot.util.state :as state]
            [electric-starter-app.bot.core.schema.validation.expression :as expressions]))

(def supported-interaction-types
  (set (keys schema.interactions/interaction-schemas)))

(defn matches-nested-value? [expected actual]
  (log/debug "Comparing nested values"
    {:expected expected
     :actual actual
     :expected-type (type expected)
     :actual-type (type actual)})
  (cond
    ;; Handle expression maps - extract actual value
    (and (map? actual)
      (:type actual)
      (= :value (:type actual)))
    (matches-nested-value? expected (:value actual))

    (map? expected)
    (do
      (log/debug "Comparing maps"
        {:expected-keys (keys expected)
         :actual-keys (if (map? actual) (keys actual) "not-a-map")})
      (every? (fn [[k v]]
                (when-let [actual-v (get actual k)]
                  (matches-nested-value? v actual-v)))
        expected))

    (vector? expected)
    (and (vector? actual)
      (= (count expected) (count actual))
      (every? true? (map matches-nested-value? expected actual)))

    (set? expected)
    (and (set? actual)
      (= (count expected) (count actual))
      (every? #(some (fn [actual-val] (matches-nested-value? % actual-val)) actual) expected))

    (coll? expected)
    (and (coll? actual)
      (= (count expected) (count actual))
      (every? true? (map matches-nested-value? expected actual)))

    :else
    (do
      (log/debug "Comparing values directly"
        {:expected expected
         :actual actual
         :equal? (= expected actual)})
      (= expected actual))))

(defn resolve-filter-value
  "Resolves filter values, handling both expressions and nested structures"
  [value context]
  (cond
    ;; Handle explicit expressions (including path expressions)
    (and (map? value) (:type value))
    (expressions/evaluate-expression value context)

    ;; Handle nested maps
    (map? value)
    (reduce-kv
      (fn [acc k v]
        (assoc acc k (resolve-filter-value v context)))
      {}
      value)

    ;; Handle sequences
    (sequential? value)
    (mapv #(resolve-filter-value % context) value)

    ;; Return primitive values as-is
    :else value))

(defn matches-filters?
  "Check if interaction matches the input filters given context"
  [input-filters interaction context]
  (when input-filters  ; Guard against nil filters
    (try
      (every? (fn [[field expected-value]]
                (let [resolved-expected (resolve-filter-value expected-value context)
                      actual-value (get-in interaction
                                     (if (vector? field)
                                       field
                                       [field]))
                      matches? (matches-nested-value? resolved-expected actual-value)]
                  (log/debug "Checking input filter match"
                    {:field field
                     :expected expected-value
                     :resolved-expected resolved-expected
                     :actual actual-value
                     :matches? matches?})
                  matches?))
        input-filters)
      (catch Exception e
        (log/error e "Error evaluating filters"
          {:filters input-filters
           :interaction-type (:interaction-type interaction)})
        false))))

(defn find-waiting-contexts
  "Find all contexts with a matching waiting state for the given interaction.
   Enhanced with better validation and error handling."
  [interaction]
  (log/debug "Searching for waiting contexts"
    {:interaction-type (:interaction-type interaction)
     :message-id (get interaction :message-id)})

  (try
    (when-not (:interaction-type interaction)
      (throw (ex-info "Invalid interaction: missing type"
               {:interaction interaction})))

    (when-not (supported-interaction-types (:interaction-type interaction))
      (log/warn "Unsupported interaction type"
        {:type (:interaction-type interaction)
         :supported supported-interaction-types})
      [])

    (let [contexts (get-in @state/state [:flows :contexts])
          _ (log/debug "Examining contexts"
              {:total-contexts (count contexts)})

          matching-contexts
          (for [[context-id context] contexts
                :let [_ (log/debug "Examining context for matches"
                          {:context-id context-id
                           :waiting-state-keys (keys (:waiting-state context))
                           :has-waiting? (seq (:waiting-state context))
                           :current-step (:current-step-index context)})
                      validated-context (try
                                          (when context  ; Only validate if context exists
                                            (schema.context/validate-context context))
                                          (catch Exception e
                                            (log/error e "Invalid context found"
                                              {:context-id context-id})
                                            nil))]
                :when (and validated-context
                        (seq (:waiting-state validated-context)))
                [wait-name wait-state] (:waiting-state validated-context)
                :let [_ (log/debug "Checking wait state"
                          {:context-id context-id
                           :wait-name wait-name
                           :wait-state-type (:interaction-type wait-state)
                           :has-interaction? (boolean (:interaction wait-state))
                           :has-filters? (boolean (:input-filters wait-state))})]
                :when (try
                        (and (keyword? wait-name)
                          (map? wait-state)
                          (nil? (:interaction wait-state))
                          (= (:interaction-type wait-state)
                            (:interaction-type interaction))
                          (matches-filters?
                            (:input-filters wait-state)
                            interaction
                            validated-context))
                        (catch Exception e
                          (log/error e "Error matching wait state"
                            {:context-id context-id
                             :wait-name wait-name})
                          false))]
            [context-id validated-context wait-name])]

      (log/debug "Found waiting contexts"
        {:examined (count contexts)
         :matched (count matching-contexts)
         :matches (mapv (fn [[id _ _]] id) matching-contexts)})

      matching-contexts)

    (catch Exception e
      (log/error e "Failed to find waiting contexts"
        {:interaction-type (:interaction-type interaction)})
      [])))

(defn evaluate-output-filter
  "Evaluates a single output filter against interaction and context"
  [{:keys [conditions]} interaction context]
  (every? (fn [[field expected-value]]
            (let [resolved-expected (resolve-filter-value expected-value context)
                  actual-value (if (vector? field)
                                 (get-in interaction field)
                                 (get-in interaction (if (string? field)
                                                       (str/split field #"\.")
                                                       [field])))
                  matches? (matches-nested-value? resolved-expected actual-value)]
              (log/debug "Checking output filter condition"
                {:field field
                 :expected expected-value
                 :resolved-expected resolved-expected
                 :actual actual-value
                 :matches? matches?})
              matches?))
    conditions))

(defn evaluate-output-filters
  "Evaluates output filters against an interaction and context.
   Returns the target step index of the first matching filter, or nil if none match."
  [output-filters interaction context]
  (log/debug "Evaluating output filters"
    {:filter-count (count output-filters)})
  (when (seq output-filters)
    (->> output-filters
      (filter #(evaluate-output-filter % interaction context))
      first
      :target-step)))