(ns com.timezynk.domain.schema
  (:refer-clojure :exclude [map vector string number boolean time] )
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

(defn defproptype* [kword validate-fn msg-str pack-fn]
  (defmethod validate-type kword [_]
    [validate-fn msg-str])
  (defmethod pack-property kword [_ v _]
    (pack-fn v))
  (t kword))

(defmacro defproptype [n validate-fn msg-str pack-fn]
  (let [kword (keyword n)]
    `(def ~n
       (defproptype* ~kword
         ~validate-fn
         ~msg-str
         ~pack-fn))))


                                        ; Standard Types

(defproptype string
  string? "not a string"
  #(when %
     (if (string? %)
       (let [^String trimmed (s/trim %)]
         (when-not (.isEmpty ^String trimmed) trimmed))
       (.toString %))))

(defproptype number
  number? "not a number"
  #(if (string? %)
     (edn/read-string %)
     %))

(defproptype boolean
  #(or (true? %) (false? %)) "not a boolean"
  #(if (string? %)
     (edn/read-string %)
     (if % true false)))

(defproptype timestamp
  #(and (number? %) (<= 0 %)) "not a valid timestamp"
  #(if (string? %)
     (edn/read-string %)
     %))

                                        ; "Annotators"


(def any       (t :any))

(defn vector [children & options]
  (apply (t :vector) :children children options))

(defn map [properties & options]
  (apply (t :map) :properties properties options))

(defn maps [properties & options]
  (apply vector (map properties)
         options))


                                        ; Validation

(defmethod validate-type :vector [_]
  [sequential? "not sequential"])

(defmethod validate-type :map [_]
  [map? "not a map"])

(defmethod validate-type :any [_]
  [(fn [_] true) ""])


                                        ; Packing

(defmethod pack-property :any [_ v props] v)

;; (defmethod pack-property :string [_ v props]
;;   (when v
;;     (if (string? v)
;;       (let [^String trimmed (s/trim v)]
;;         (when-not (.isEmpty ^String trimmed) trimmed))
;;       (.toString v))))

(defmethod pack-property :number [_ v props]
  (if (string? v)
    (edn/read-string v)
    v))

(defmethod pack-property :vector [_ v props]
  v)

(defmethod pack-property nil [trail v props]
  v)
