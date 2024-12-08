(ns electric-starter-app.bot.core.schema.definition.expression)

(def expression-schema
  "Schema for declarative value expressions in action inputs"
  {:type {:type [:enum [:path :value :map :str-concat]]
          :required true
          :doc "Type of expression - path reference, direct value, map of expressions, or string concatenation"}

   :path {:type [:vector :keyword]
          :required {:when {:type :path}}
          :doc "Vector of keys to look up in context"}

   :value {:type :any
           :required {:when {:type :value}}
           :doc "Direct value to use"}

   :expressions {:type [:map-of :keyword expression-schema]
                 :required {:when #{:map :str-concat}}
                 :doc "Map of nested expressions"}})

(defn path-expr
  "Creates a path reference expression"
  [path]
  {:type :path
   :path path})

(defn value-expr
  "Creates a direct value expression"
  [value]
  {:type :value
   :value value})

(defn map-expr
  "Creates a map of expressions"
  [exprs]
  {:type :map
   :expressions exprs})

(defn str-expr
  "Creates a string concatenation expression from a map of parts"
  [parts-map]
  {:type :str-concat
   :expressions (reduce-kv
                  (fn [acc k v]
                    (assoc acc k
                      (if (string? v)
                        {:type :value, :value v}
                        v)))
                  {}
                  parts-map)})