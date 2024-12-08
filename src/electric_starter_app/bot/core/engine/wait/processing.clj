(ns electric-starter-app.bot.core.engine.wait.processing
  (:require [clojure.tools.logging :as log]
            [electric-starter-app.bot.core.schema.validation.wait-state :refer [validate-wait-step]]
            [electric-starter-app.bot.core.schema.validation.context :as schema.context]
            [electric-starter-app.bot.core.schema.validation.expression :as expressions]
            [electric-starter-app.bot.core.schema.definition.wait-state :as wait-state]
            [electric-starter-app.bot.impl.flows :refer [available-flows]]))

(defn resolve-wait-step-expressions
  "Resolves any expressions in the wait step filters using the context."
  [{:keys [input-filters output-filters] :as wait-step} ctx]
  (cond-> wait-step
    input-filters
    (update :input-filters #(expressions/evaluate-expression % ctx))

    output-filters
    (update :output-filters
      (fn [filters]
        (mapv #(update % :conditions
                 (fn [conditions]
                   (expressions/evaluate-expression conditions ctx)))
          filters)))))

(defn get-active-wait-state
  "Retrieves the currently active waiting state if one exists.
   Returns [wait-id wait-state] or nil if no active wait state exists."
  [waiting-states]
  (log/debug "Checking for active wait states"
    {:all-states waiting-states})
  (let [active (first (filter (fn [[id v]]
                                (let [is-active (and (nil? (:interaction v))
                                                  (not (:completed v))
                                                  (not (:queue? v))) ; Ignore queue states
                                      _ (log/debug "Evaluating wait state"
                                          {:id id
                                           :state v
                                           :has-interaction (boolean (:interaction v))
                                           :is-completed (boolean (:completed v))
                                           :is-queue (boolean (:queue? v))
                                           :is-active is-active})]
                                  is-active))
                        waiting-states))]
    (log/debug "Active wait state check result"
      {:found-active (boolean active)
       :active-id (first active)})
    active))

(defn process-wait-step
  "Processes a wait step in a flow, creating and storing the wait state."
  [context wait-step]
  (log/debug "Beginning wait step processing"
    {:context-id (:id context)
     :current-step (:current-step-index context)
     :wait-step wait-step})

  (let [validated-step (validate-wait-step wait-step)
        resolved-step (resolve-wait-step-expressions validated-step context)
        is-queue? (get resolved-step :queue? false)]

    (log/debug "Resolved wait step"
      {:original wait-step
       :resolved resolved-step
       :is-queue is-queue?})

    ;; Only check for active wait states if this is not a queue wait
    (when-let [active-wait (and (not is-queue?)
                             (get-active-wait-state (:waiting-state context)))]
      (throw (ex-info "Cannot create new wait state while another is active"
               {:new-wait (:store-as resolved-step)
                :active-wait (first active-wait)})))

    (let [wait-state (wait-state/create-wait-state
                       (:interaction-type resolved-step)
                       :input-filters (:input-filters resolved-step)
                       :output-filters (:output-filters resolved-step)
                       :queue? is-queue?)]

      (log/debug "Created new wait state"
        {:wait-field (:store-as resolved-step)
         :new-state wait-state
         :is-queue is-queue?})

      (-> context
        (assoc-in [:waiting-state (:store-as resolved-step)] wait-state)
        schema.context/validate-context))))

(defn matches-nested-value?
  [expected actual]
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



(defn process-next-queued
  "Process the next queued interaction for a wait state if available.
   Returns updated context with next interaction processed, or nil if queue empty."
  [context wait-field]
  (let [wait-state (get-in context [:waiting-state wait-field])
        next-interaction (first (:queue wait-state))
        current-step-index (or (:current-step-index context) 0)
        wait-step-index (or (first
                              (keep-indexed
                                (fn [idx step]
                                  (when (and (:wait step)
                                          (= (get-in step [:wait :store-as]) wait-field))
                                    idx))
                                (:steps (get available-flows (:flow-id context)))))
                          0)]
    (when next-interaction
      (log/debug "Processing next queued interaction"
        {:context-id (:id context)
         :wait-field wait-field
         :queue-size (count (:queue wait-state))})
      (let [updated-context (-> context
                              (update-in [:waiting-state wait-field :queue] subvec 1)
                              (assoc-in [:waiting-state wait-field :interaction] next-interaction)
                              (assoc :current-step-index (inc wait-step-index)))]
        ;; Process the step after wait state (usually an action)
        (if (:action (get-in (get available-flows (:flow-id context))
                       [:steps (inc wait-step-index)]))
          ;; If there's an action step, schedule the interaction clearance after it
          (update-in updated-context [:waiting-state wait-field] assoc
            :clear-after-action true)
          ;; If no action step, clear interaction immediately
          (assoc-in updated-context [:waiting-state wait-field :interaction] nil))))))

(defn queue-interaction
  "Adds an interaction to a wait state's queue"
  [wait-state interaction]
  (log/debug "Queueing interaction in wait state"
    {:interaction-type (:interaction-type wait-state)
     :queue-size (count (:queue wait-state))})
  (update wait-state :queue (fnil conj []) interaction))

(defn get-queue-wait-states
  "Retrieves all queue-enabled wait states.
   Returns sequence of [wait-id wait-state] pairs."
  [waiting-states]
  (log/debug "Finding queue wait states")
  (let [queue-states (filter (fn [[_ v]] (:queue? v)) waiting-states)]
    (log/debug "Found queue wait states"
      {:count (count queue-states)
       :ids (map first queue-states)})
    queue-states))



(defn evaluate-output-filter
  "Evaluates a single output filter against interaction and context"
  [{:keys [conditions]} interaction context]
  (every? (fn [[field expected-value]]
            (let [resolved-expected (resolve-filter-value expected-value context)
                  actual-value (if (vector? field)
                                 (get-in interaction field)
                                 (get-in interaction [field]))
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

(defn complete-wait-state
  "Complete a wait state and advance the flow to the appropriate next step"
  [context wait-field interaction]
  (log/debug "Starting wait state completion"
    {:context-id (:id context)
     :wait-field wait-field
     :current-step (:current-step-index context)
     :waiting-states (:waiting-state context)})

  (let [wait-state (get-in context [:waiting-state wait-field])
        output-filters (:output-filters wait-state)
        target-step (when output-filters
                      (evaluate-output-filters
                        output-filters
                        interaction
                        context))
        next-step (or target-step (inc (:current-step-index context)))
        updated-context (-> context
                          (assoc-in [:waiting-state wait-field :interaction] interaction)
                          (assoc-in [:waiting-state wait-field :completed] true)
                          (assoc :current-step-index next-step))]

    (log/debug "Completed wait state"
      {:context-id (:id context)
       :wait-field wait-field
       :had-output-filters? (boolean output-filters)
       :target-step target-step
       :next-step next-step
       :updated-waiting-states (:waiting-state updated-context)})

    (schema.context/validate-context updated-context)))