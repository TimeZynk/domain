(ns com.timezynk.domain.validation.validation-operator
  (:require
   [clojure.test :refer [deftest is]]))

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

(deftest test-validation-operator
  (let [operator-func1 (fn [_] true)
        operator-func2 (fn [_] false)
        error-func (fn [errs] errs)
        rules [(fn [entry] [true (str "True entry: " entry)])
               (fn [entry] [false (str "False entry: " entry)])]]
    (is (= [true {}]
           ((validation-operator operator-func1 error-func rules) "Entry")))
    (is (= [false ["True entry: Entry" "False entry: Entry"]]
           ((validation-operator operator-func2 error-func rules) "Entry")))))
