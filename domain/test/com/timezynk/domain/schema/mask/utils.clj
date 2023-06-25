(ns com.timezynk.domain.schema.mask.utils)

(defn action-matcher [value]
  (fn [[_ _ action _]]
    (= value action)))

(defn property-name-matcher [value]
  (fn [[_ _ _ property-name]]
    (= value property-name)))
