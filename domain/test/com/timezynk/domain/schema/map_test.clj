(ns com.timezynk.domain.schema.map-test
  (:require [clojure.test :refer [deftest is testing]]
            [slingshot.test]
            [com.timezynk.domain.schema :as s]
            [com.timezynk.domain.utils :as u])
  (:import [org.bson.types ObjectId]))

(def ^:private dtc
  (u/dtc {:x (s/map {:id (s/id)
                     :ref-no (s/integer)
                     :sold (s/boolean)})}))

(def ^:private ^:const valid-doc
  {:x {:id (ObjectId.)
       :ref-no 123
       :sold true}})

(deftest valid
  (is (u/insert dtc valid-doc)))

(deftest map-value-not-matching-key-type
  (testing "type mismatch within map"
    (let [invalid-doc-1 (update-in valid-doc [:x :id] str)
          invalid-doc-2 (update-in valid-doc [:x :ref-no] str)
          invalid-doc-3 (assoc-in valid-doc [:x :sold] 1)]
      (testing "string instead of ObjectId"
        (is (thrown+? (= (get-in % [:errors :id]) "not a valid id")
                      (u/insert dtc invalid-doc-1))))
      (testing "string instead of integer"
        (is (thrown+? (= (get-in % [:errors :ref-no]) "not an integer")
                      (u/insert dtc invalid-doc-2))))
      (testing "integer instead of boolean"
        (is (thrown+? (= (get-in % [:errors :sold]) "not a boolean")
                      (u/insert dtc invalid-doc-3)))))))

(deftest non-map-instead-of-map
  (let [invalid-doc-1 (assoc valid-doc :x "abc")
        invalid-doc-2 (assoc valid-doc :x 123)
        invalid-doc-3 (assoc valid-doc :x false)]
    (testing "string instead of map"
      (is (thrown+? (= (get-in % [:errors :x]) "not a map")
                    (u/insert dtc invalid-doc-1))))
    (testing "integer instead of map"
      (is (thrown+? (= (get-in % [:errors :x]) "not a map")
                    (u/insert dtc invalid-doc-2))))
    (testing "boolean instead of map"
      (is (thrown+? (= (get-in % [:errors :x]) "not a map")
                    (u/insert dtc invalid-doc-3))))))
