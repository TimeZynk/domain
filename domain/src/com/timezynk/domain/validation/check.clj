(ns com.timezynk.domain.validation.check
  (:require
   [clojure.test :refer [deftest is]]))

(defn check [fun attr-name msg]
  (fn [val]
    (if (nil? val)
      [false {attr-name "required property has no value"}]
      (if (fun val)
        [true {}]
        [false {attr-name msg}]))))

(deftest test-check
  (is (= [true {}]
         ((check (fn [_] ()) :field1 "msg") "")))
  (is (= [false {:field1 "required property has no value"}]
         ((check (fn [_] ()) :field1 "msg") nil)))
  (is (= [true {}]
         ((check (fn [x] (= x 1)) :field1 "msg") 1)))
  (is (= [false {:field1 "msg"}]
         ((check (fn [x] (= x 1)) :field1 "msg") 2))))
