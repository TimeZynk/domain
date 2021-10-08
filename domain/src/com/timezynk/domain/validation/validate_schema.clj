(ns com.timezynk.domain.validation.validate-schema
  (:require
   [com.timezynk.domain.validation.only-these :refer [only-these]]
   [com.timezynk.domain.validation.validate-property :refer [validate-property]]
   [com.timezynk.domain.validation.operator.all-of :refer [all-of]]))

(defn validate-schema
  "Deprecated"
  [all-optional? schema m]
  (let [{:keys [properties _after-pack-rule]} schema
        only-these-keys                       (apply only-these (keys properties))
        rule                                  (->> properties
                                                   (map #(partial validate-property % all-optional?))
                                                   (cons only-these-keys)
                                                   (apply all-of))]
    (rule m)))
