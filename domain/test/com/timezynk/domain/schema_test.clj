(ns com.timezynk.domain.schema-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [com.timezynk.domain.core :as c]
            [com.timezynk.domain.schema :as s]
            [com.timezynk.domain.persistence :as p])
  (:use [slingshot.test])
  (:import [org.bson.types ObjectId]))

(def ^:dynamic *dtc*)

(def ^:const properties
  {:x (s/id)
   :y (s/map {:id (s/id)
              :ref-no (s/integer)
              :sold (s/boolean)})})

(defn- create-dtc [f]
  (binding [*dtc* (c/dom-type-collection :name :cars
                                         :properties properties)]
    (f)))

(def ^:const valid-doc
  {:x (ObjectId.)
   :y {:id (ObjectId.)
       :ref-no 42
       :sold true}
   :company-id (ObjectId.)})

(use-fixtures :each create-dtc)

(defn- insert! [doc]
  @(p/conj! *dtc* doc))

(deftest valid
  (let [before @(p/select-count *dtc*)
        _ (insert! valid-doc)
        after @(p/select-count *dtc*)]
    (is (= after (inc before)))))

(deftest map-value-not-matching-key-type
  (testing "type mismatch within map"
    (let [invalid-doc-1 (update-in valid-doc [:y :id] str)
          invalid-doc-2 (update-in valid-doc [:y :ref-no] str)
          invalid-doc-3 (assoc-in valid-doc [:y :sold] 1)]
      (testing "string instead of ObjectId"
        (is (thrown+? (= (get-in % [:errors :id]) "not a valid id")
                      (insert! invalid-doc-1))))
      (testing "string instead of integer"
        (is (thrown+? (= (get-in % [:errors :ref-no]) "not an integer")
                      (insert! invalid-doc-2))))
      (testing "integer instead of boolean"
        (is (thrown+? (= (get-in % [:errors :sold]) "not a boolean")
                      (insert! invalid-doc-3)))))))

(deftest non-map-instead-of-map
  (let [invalid-doc-1 (assoc valid-doc :y "abc")
        invalid-doc-2 (assoc valid-doc :y 42)
        invalid-doc-3 (assoc valid-doc :y false)]
    (testing "string instead of map"
      (is (thrown+? (= (get-in % [:errors :y]) "not a map")
                    (insert! invalid-doc-1))))
    (testing "integer instead of map"
      (is (thrown+? (= (get-in % [:errors :y]) "not a map")
                    (insert! invalid-doc-2))))
    (testing "boolean instead of map"
      (is (thrown+? (= (get-in % [:errors :y]) "not a map")
                    (insert! invalid-doc-3))))))
