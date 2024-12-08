(ns electric-starter-app.bot.core.schema.validation.expression
  (:require [clojure.tools.logging :as log]
            [clojure.string :as str]))

(declare evaluate-expression)

(defn is-expression? [v]
  (and (map? v)
    (contains? v :type)
    (contains? #{:path :value :map :str-concat} (:type v))))

(defn validate-path
  "Validates a path vector against the schema"
  [path]
  (when-not (vector? path)
    (throw (ex-info "Path must be a vector"
             {:path path
              :type (type path)})))

  (when-not (every? keyword? path)
    (throw (ex-info "Path must contain only keywords"
             {:path path
              :invalid-elements (remove keyword? path)})))
  path)

(defn validate-expression
  "Validates an expression against the expression schema.
   Returns the expression if valid, throws if invalid."
  [expr]
  (when-not (map? expr)
    (throw (ex-info "Expression must be a map"
             {:value expr
              :type (type expr)})))

  (when-not (contains? expr :type)
    (throw (ex-info "Expression must have a :type"
             {:expression expr})))

  (when-not (#{:path :value :map :str-concat} (:type expr))
    (throw (ex-info "Invalid expression type"
             {:type (:type expr)
              :valid-types #{:path :value :map :str-concat}})))

  (case (:type expr)
    :path (do
            (when-not (contains? expr :path)
              (throw (ex-info "Path expression must have :path key"
                       {:expression expr})))
            (validate-path (:path expr)))

    :value (when-not (contains? expr :value)
             (throw (ex-info "Value expression must have :value key"
                      {:expression expr})))

    (:map :str-concat)
    (do
      (when-not (contains? expr :expressions)
        (throw (ex-info "Expression must have :expressions key"
                 {:expression expr})))
      (when-not (map? (:expressions expr))
        (throw (ex-info "Expressions value must be a map"
                 {:expression expr
                  :expressions (:expressions expr)})))))

  expr)

(defn evaluate-map-expressions
  "Evaluates a map of expressions and combines the results"
  [expressions ctx]
  (reduce-kv
    (fn [acc k v]
      (assoc acc k (evaluate-expression v ctx)))
    {}
    expressions))

(defn evaluate-expression
  "Evaluates an expression based on the expression schema, returning resolved value.
   Handles path references, direct values, nested maps and type/value structures."
  [expr ctx]
  (when expr
    (log/debug "Evaluating expression" {:expr expr :type (type expr)})
    (cond
      ;; Handle type/value maps - commonly used in discord data
      (and (map? expr) (:type expr) (= :value (:type expr)))
      (do
        (log/debug "Found type/value map" {:value (:value expr)})
        (:value expr))

      ;; Handle expression schema types
      (and (map? expr) (:type expr))
      (case (:type expr)
        :path (get-in ctx (:path expr))
        :value (:value expr)
        :map (reduce-kv
               (fn [acc k v]
                 (assoc acc k (evaluate-expression v ctx)))
               {}
               (:expressions expr))
        :str-concat (let [resolved-exprs (evaluate-map-expressions (:expressions expr) ctx)]
                      (str/join "" (vals resolved-exprs)))
        (throw (ex-info "Unknown expression type"
                 {:expression expr
                  :type (:type expr)})))

      ;; Handle nested maps that might contain expressions
      (map? expr)
      (reduce-kv
        (fn [m k v]
          (assoc m k (evaluate-expression v ctx)))
        {}
        expr)

      ;; Handle vector of expressions
      (vector? expr)
      (mapv #(evaluate-expression % ctx) expr)

      ;; Default - return as is
      :else expr)))



(defn evaluate-str-concat-expressions
  "Evaluates expressions and concatenates them into a string"
  [expressions ctx]
  (->> expressions
    (evaluate-map-expressions ctx)
    vals
    (map str)  ;; Ensure everything is converted to string
    (apply str)))

