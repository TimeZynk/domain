(ns com.timezynk.domain.schema.walk
  (:require [clojure.walk :as w]))

(defn- pairmaker [spec]
  #(hash-map :value % :spec spec))

(defmulti zipper
  "Builds a function which takes a single argument: a domain document.
   The function \"zips\" the document with `schema`, i.e. decorates each of its
   values with the corresponding property definition."
  {:private true}
  (fn [schema] (get schema :type :top-level)))

(defmethod zipper :top-level
  [schema]
  (fn [doc]
    (->> schema
         (filter #(contains? doc (key %)))
         (reduce-kv (fn [acc k v]
                      (update acc k (comp (pairmaker v) (zipper v))))
                    doc))))

(defmethod zipper :vector
  [schema]
  (let [wrap (fn [x] {:_ x})
        unwrap :_
        zip (zipper {:_ (:children schema)})]
    (fn [doc]
      (mapv (comp unwrap zip wrap) doc))))

(defmethod zipper :map
  [schema]
  (fn [doc]
    (let [zip (zipper (:properties schema))]
      (zip doc))))

(defmethod zipper :default
  [_]
  identity)

(defn- pair? [x]
  (and (map? x)
       (contains? x :value)
       (contains? x :spec)))

(defn- unzipper [update-fn]
  (fn [doc]
    (w/postwalk #(if (pair? %)
                   (update-fn (:value %) (:spec %))
                   %)
                doc)))

(defn update-properties
  "Replaces each value `v` in `doc` with the result of `(update-fn v s)`, where:
    * `v` is the value associated with property `k`
    * `s` is the definition of property `k`"
  [doc schema update-fn]
  (let [zip (zipper schema)
        unzip (unzipper update-fn)]
    (->> doc zip unzip)))
