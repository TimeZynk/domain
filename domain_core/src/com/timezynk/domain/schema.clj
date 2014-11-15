(ns com.timezynk.domain.schema
  (:refer-clojure :exclude [map sequence string number boolean time] )
  (:require [clojure.core                     :as c]
            [clojure.core.reducers            :as r]
            [com.timezynk.domain.update-leafs :refer [update-leafs]]
            [clojure.string                   :as s]
            [clojure.edn                      :as edn]
            [com.timezynk.domain.validation   :as v :refer [validate-type]]
            [com.timezynk.domain.pack         :as pack :refer [pack-property]]))

(defn t [type]
  (fn [& {:as options}]
    (merge {:type type} options)))

(defn proptype [& {:keys [key validate invalid-msg pack declare]}]
  (defmethod validate-type key [_]
    [validate invalid-msg])
  (defmethod pack-property key [_ v _]
    ((or pack identity) v))
  (or declare (t key)))

(defmacro defproptype [n & {:keys [validate invalid-msg pack declare]}]
  (let [kword (keyword n)]
    `(def ~n
       (proptype :key ~kword
                 :validate ~validate
                 :invalid-msg ~invalid-msg
                 :pack ~pack
                 :declare ~declare))))


                                        ; Standard Types

(defproptype any
  :validate (constantly true)
  :invalid-msg "")


(defproptype string
  :validate string?
  :invalid-msg "not a string"
  :pack #(when %
           (if (string? %)
             (let [^String trimmed (s/trim %)]
               (when-not (.isEmpty ^String trimmed) trimmed))
             (.toString %))))

(defproptype number
  :validate number?
  :invalid-msg "not a number"
  :pack #(if (string? %) (edn/read-string %) %))

(defproptype boolean
  :validate #(or (true? %) (false? %))
  :invalid-msg "not a boolean"
  :pack #(if (string? %)
           (edn/read-string %)
           (if % true false)))

(defproptype timestamp
  :validate #(and (number? %) (<= 0 %))
  :invalid-msg "not a valid timestamp"
  :pack #(if (string? %) (edn/read-string %) %))

(defproptype sequence
  :validate sequential?
  :invalid-msg "not a sequence"
  :declare (fn sequence [children & options]
             (apply (t :sequence) :children children options)))

(defproptype map
  :validate map?
  :invalid-msg "not a map"
  :declare (fn map [properties & options]
             (apply (t :map) :properties properties options)))

(defn maps [properties & options]
  (apply sequence (map properties)
         options))
