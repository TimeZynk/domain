(ns com.timezynk.domain.validation.operator.all-of
  (:require
   [com.timezynk.domain.validation.operator.validation-operator :refer [validation-operator]]))

(defn all-of
  "All conditions should be true (AND)"
  [& rules]
  (validation-operator (fn [results] (reduce #(and % %2) results))
                       (fn [errs] (apply merge errs))
                       rules))
