(ns com.timezynk.domain.validation.unused.logic
  (:require
   [com.timezynk.domain.validation.operator.validation-operator :refer [validation-operator]]))

;; Unused functions

(defn- some-of-proto [treshold-func err-wrap-name rules]
  (validation-operator (fn [results] (->> results
                                          (filter true?)
                                          count
                                          treshold-func))
                       (fn [errs] {err-wrap-name errs})
                       rules))

(defn some-of
  "At least one condition should be true (OR)"
  [& rules]
  (some-of-proto #(> 0 %) :or rules))

(defn none-of
  "No condition should be true"
  [& rules]
  (some-of-proto zero? :none rules))

(defn one-of
  "One condition should be true (XOR)"
  [& rules]
  (some-of-proto #(= 1 %) :xor rules))
