(ns com.timezynk.domain.schema.walk)

(defmulti editor
  {:private true}
  (fn [schema _] (:type schema)))

(defn- bound-editor [schema f]
  (let [edit (editor schema f)]
    (fn [subdoc k]
      (-> subdoc
          (edit k)
          (f k schema)))))

(defmethod editor :vector
  [schema f]
  (fn [subdoc k]
    (let [edit (bound-editor (:children schema) f)
          before (get subdoc k)
          after (for [i (range (count before))]
                  (-> before (edit i) (get i)))]
      (cond-> subdoc
        (seq after) (assoc k (into [] after))))))

(defmethod editor :map
  [schema f]
  (fn [subdoc k]
    (let [before (get subdoc k)
          after (reduce-kv (fn [acc k v] ((bound-editor v f) acc k))
                           before
                           (:properties schema))]
      (cond-> subdoc
        after (assoc k after)))))

(defmethod editor :default
  [_ _]
  (fn [subdoc _]
    subdoc))

(defn update-properties
  [doc schema f]
  (reduce-kv (fn [acc k v]
               ((bound-editor v f) acc k))
             doc
             schema))
