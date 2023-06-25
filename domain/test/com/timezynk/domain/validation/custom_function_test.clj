(ns com.timezynk.domain.validation.custom-function-test
  (:require [clojure.test :refer [deftest is]]
            [slingshot.test]
            [com.timezynk.domain.schema :as s]
            [com.timezynk.domain.utils :as u]))

(def ^:private ^:const ERRORS "xyz")

(def ^:private good (constantly nil))

(def ^:private bad (constantly ERRORS))

(deftest success
  (let [dtc (u/dtc {:x (s/number :validate good)})
        out (u/insert dtc {:x 1})]
    (is (some? out))))

(deftest failure
  (let [dtc (u/dtc {:x (s/number :validate bad)})
        f #(u/insert dtc {:x 1})]
    (is (thrown+? (= :validation-error (:type %))     (f)))
    (is (thrown+? (= :x                (:property %)) (f)))
    (is (thrown+? (= ERRORS            (:errors %))   (f)))))
