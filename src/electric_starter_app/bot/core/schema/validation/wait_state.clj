(ns electric-starter-app.bot.core.schema.validation.wait-state
  (:require [clojure.tools.logging :as log]
            [electric-starter-app.bot.core.schema.definition.wait-state :refer [waiting-state-schema]]
            [electric-starter-app.bot.platform.discord.schema.definition.interaction :as schema.interactions]))

(defn validate-waiting-state-entry
  "Validates a single waiting state entry against the schema.
   Returns true if valid, throws detailed exception if invalid."
  [wait-id entry]
  (log/debug "Validating waiting state entry" {:wait-id wait-id :entry entry})

  (doseq [[field spec] waiting-state-schema]
    (when (:required spec)
      (when-not (contains? entry field)
        (throw (ex-info "Missing required field in waiting state"
                 {:wait-id wait-id
                  :missing-field field
                  :entry entry}))))

    (when-let [validate (:validation spec)]
      (let [value (get entry field)]
        (when (and value (not (validate value)))
          (throw (ex-info "Invalid field value in waiting state"
                   {:wait-id wait-id
                    :field field
                    :value value}))))))

  ;; Additional validation for output filters structure
  (when-let [output-filters (:output-filters entry)]
    (when-not (vector? output-filters)
      (throw (ex-info "Output filters must be a vector"
               {:wait-id wait-id
                :output-filters output-filters})))

    (doseq [filter output-filters]
      (when-not (and (map? filter)
                  (contains? filter :target-step)
                  (contains? filter :conditions)
                  (number? (:target-step filter))
                  (map? (:conditions filter)))
        (throw (ex-info "Invalid output filter structure"
                 {:wait-id wait-id
                  :filter filter})))))

  true)

(defn validate-waiting-states
  "Validates the complete waiting-state map structure.
   Returns true if valid, throws detailed exception if invalid."
  [waiting-states]
  (log/debug "Validating waiting states structure" {:states waiting-states})
  (when-not (map? waiting-states)
    (throw (ex-info "waiting-state must be a map"
             {:value waiting-states
              :type (type waiting-states)})))

  (doseq [[wait-id entry] waiting-states]
    (when-not (keyword? wait-id)
      (throw (ex-info "Waiting state ID must be a keyword"
               {:wait-id wait-id
                :type (type wait-id)})))

    (when-not (map? entry)
      (throw (ex-info "Waiting state entry must be a map"
               {:wait-id wait-id
                :value entry
                :type (type entry)})))

    (validate-waiting-state-entry wait-id entry))

  ;; Split regular and queue wait states
  (let [regular-waits (filter (fn [[_ v]]
                                (and (not (:queue? v))
                                  (nil? (:interaction v))))
                        waiting-states)
        queue-waits (filter (fn [[_ v]] (:queue? v)) waiting-states)]

    ;; Ensure only one regular wait state is active at a time
    (when (> (count regular-waits) 1)
      (throw (ex-info "Multiple active regular wait states detected"
               {:active-wait-ids (map first regular-waits)})))

    ;; Ensure queue wait states have valid queue
    (doseq [[wait-id entry] queue-waits]
      (when-not (vector? (:queue entry))
        (throw (ex-info "Queue must be a vector"
                 {:wait-id wait-id
                  :queue (:queue entry)})))))

  true)

(defn validate-wait-step
  "Validates a wait step specification from a flow definition"
  [{:keys [interaction-type input-filters output-filters store-as queue?] :as wait-step}]
  (log/debug "Validating wait step"
    {:interaction-type interaction-type
     :store-as store-as
     :queue? queue?})

  (when-not interaction-type
    (throw (ex-info "Wait step missing interaction-type"
             {:wait-step wait-step})))

  (when-not store-as
    (throw (ex-info "Wait step missing store-as field"
             {:wait-step wait-step})))

  (when-not (keyword? store-as)
    (throw (ex-info "Wait step store-as must be a keyword"
             {:store-as store-as
              :type (type store-as)})))

  ;; Validate input filters structure if present
  (when input-filters
    (when-not (map? input-filters)
      (throw (ex-info "Input filters must be a map"
               {:input-filters input-filters}))))

  ;; Validate output filters if present
  (when output-filters
    (when-not (vector? output-filters)
      (throw (ex-info "Output filters must be a vector"
               {:wait-step wait-step}))))

  wait-step)

(defn validate-waiting-state
  "Validates a waiting state specification against schema.
   Checks interaction type and optional filters."
  [waiting-state]
  (log/debug "Validating waiting state" {:state waiting-state})
  (let [{:keys [interaction-type filters]} waiting-state]
    ;; Validate interaction type exists
    (when-not (contains? schema.interactions/interaction-schemas interaction-type)
      (throw (ex-info "Invalid interaction type in waiting state"
               {:type interaction-type
                :valid-types (keys schema.interactions/interaction-schemas)})))

    ;; Validate filters against schema if present
    (when filters
      (let [schema-fields (get-in schema.interactions/interaction-schemas [interaction-type :fields])]
        (doseq [[field-path _] filters]
          (let [field-key (if (vector? field-path) (first field-path) field-path)]
            (when-not (contains? schema-fields field-key)
              (throw (ex-info "Invalid filter field in waiting state"
                       {:field field-key
                        :path field-path
                        :valid-fields (keys schema-fields)})))))))
    waiting-state))