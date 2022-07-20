(ns com.timezynk.domain.schema.map-test
  (:require [clojure.test :refer [deftest is are testing]]
            [com.timezynk.domain.schema :as s]
            [com.timezynk.domain.validation.validate :refer [validate-schema]])
  (:import [org.bson.types ObjectId]))

(def ^:const valid-value
  {:id (ObjectId.)
   :ref-no 42
   :sold true})

(defn- validator [value]
  (validate-schema false
                   {:properties {:field (s/map {:id (s/id)
                                                :ref-no (s/integer)
                                                :sold (s/boolean)})}}
                   {:field value}))

(deftest valid
  (is (= [true {}]
         (validator valid-value))))

(deftest map-value-not-matching-key-type
  (testing "type mismatch within map"
    (let [invalid-value-1 (update valid-value :id str)
          invalid-value-2 (update valid-value :ref-no str)
          invalid-value-3 (assoc valid-value :sold 1)]
      (testing "string instead of ObjectId"
        (is (= [false {:id "not a valid id"}]
               (validator invalid-value-1))))
      (testing "string instead of integer"
        (is (= [false {:ref-no "not an integer"}]
               (validator invalid-value-2))))
      (testing "integer instead of boolean"
        (is (= [false {:sold "not a boolean"}]
               (validator invalid-value-3)))))))

(deftest non-map-instead-of-map
  (are [value] (let [[ok? errors] (validator value)]
                 (and (false? ok?)
                      (= "not a map" (:field errors))))
       "abc"
       123
       false))
