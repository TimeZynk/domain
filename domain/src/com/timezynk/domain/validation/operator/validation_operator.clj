(ns com.timezynk.domain.validation.operator.validation-operator)

(defn validation-operator [operator-func error-func rules]
  (fn [entry]
    (let [results (map #(% entry) rules)
          valid?  (->> results
                       (map first)
                       operator-func)]
      (if valid?
        [true {}]
        [false (->> results
                    (map second)
                    error-func)]))))
