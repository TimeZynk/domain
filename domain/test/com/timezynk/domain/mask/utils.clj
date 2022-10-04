(ns com.timezynk.domain.mask.utils)

(defn action-matcher [value]
  (fn [[_ _ action _]]
    (= value action)))

(defn property-name-matcher [value]
  (fn [[_ _ _ property-name]]
    (= value property-name)))
