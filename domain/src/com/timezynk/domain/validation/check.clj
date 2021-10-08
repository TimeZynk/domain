(ns com.timezynk.domain.validation.check)

(defn check [fun attr-name msg]
  (fn [val]
    (if (nil? val)
      [false {attr-name "required property has no value"}]
      (if (fun val)
        [true {}]
        [false {attr-name msg}]))))
