(ns com.timezynk.domain.schema
  (:refer-clojure :exclude [map vector string number boolean time] )
  (:require [clojure.core                     :as c]
            [clojure.core.reducers            :as r]
            [com.timezynk.domain.update-leafs :refer [update-leafs]]
            [clojure.string                   :as s]
            [clojure.edn                      :as edn]
            [com.timezynk.domain.validation   :as v :refer [validate-type]]
            [com.timezynk.domain.pack         :as pack :refer [pack-property]]))


                                        ; "Annotators"

(defn t [type]
  (fn [& {:as options}]
    (merge {:type type} options)))

(def string    (t :string))
(def number    (t :number))
(def boolean   (t :boolean))
(def timestamp (t :timestamp))
(def any       (t :any))

(defn vector [children & options]
  (apply (t :vector) :children children options))

(defn map [properties & options]
  (apply (t :map) :properties properties options))

(defn maps [properties & options]
  (apply vector (map properties)
         options))


                                        ; Validation

(defmethod validate-type :string [_]
  [string? "not a string"])

(defmethod validate-type :number [_]
  [number? "not a number"])

(defmethod validate-type :vector [_]
  [sequential? "not sequential"])

(defmethod validate-type :map [_]
  [map? "not a map"])

(defn timestamp? [x]
  (and (number? x) (<= 0 x)))

(defmethod validate-type :timestamp [_]
  [timestamp? "not a valid timestamp"])

(defn boolean? [s] (or (true? s) (false? s)))

(defmethod validate-type :boolean [_]
  [boolean? "not a boolean"])

(defmethod validate-type :any [_]
  [(fn [_] true) ""])


                                        ; Packing

(defmethod pack-property :any [_ v props] v)

(defmethod pack-property :string [_ v props]
  (when v
    (if (string? v)
      (let [^String trimmed (s/trim v)]
        (when-not (.isEmpty ^String trimmed) trimmed))
      (.toString v))))

(defmethod pack-property :number [_ v props]
  (if (string? v)
    (edn/read-string v)
    v))

(defmethod pack-property :timestamp [_ v props]
  (if (string? v)
    (edn/read-string v)
    v))

(defmethod pack-property :boolean [_ v props]
  (if (string? v)
    (edn/read-string v)
    (if v true false)))

(defmethod pack-property :vector [_ v props]
  v)

(defmethod pack-property nil [trail v props]
  v)
