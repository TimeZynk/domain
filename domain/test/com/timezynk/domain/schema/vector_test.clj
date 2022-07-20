(ns com.timezynk.domain.schema.vector-test
  (:require [clojure.test :refer [deftest is are testing]]
            [com.timezynk.domain.schema :as s]
            [com.timezynk.domain.validation.validate :refer [validate-schema]])
  (:import [org.bson.types ObjectId]))

(def ^:const valid-value
  [1 2 3])

(defn- validator [value]
  (validate-schema false
                   {:properties {:field (s/vector (s/integer))}}
                   {:field value}))

(deftest valid
  (is (= [true {}]
         (validator valid-value))))

(deftest vector-value-not-matching-item-type
  (testing "type mismatch within vector"
    (let [invalid-value-1 (update valid-value 0 str)
          invalid-value-2 (update valid-value 1 boolean)
          invalid-value-3 (update valid-value 2 #(hash-map :x %))]
      (testing "string instead of integer"
        (is (= [false {:field {0 "not an integer"}}]
               (validator invalid-value-1))))
      (testing "boolean instead of integer"
        (is (= [false {:field {1 "not an integer"}}]
               (validator invalid-value-2))))
      (testing "map instead of integer"
        (is (= [false {:field {2 "not an integer"}}]
               (validator invalid-value-3)))))))

(deftest non-vector-instead-of-vector
  (are [value] (let [[ok? errors] (validator value)]
                 (and (false? ok?)
                      (= "not sequential" (:field errors))))
       "abc"
       123
       false))
