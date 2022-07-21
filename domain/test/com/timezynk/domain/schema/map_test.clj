(ns com.timezynk.domain.schema.map-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [com.timezynk.assembly-line :as a]
            [com.timezynk.domain.core :as c]
            [com.timezynk.domain.persistence :as p]
            [com.timezynk.domain.schema :as s])
  (:use [slingshot.test])
  (:import [org.bson.types ObjectId]))

(def ^:private ^:dynamic *dtc*)

(def ^:private ^:const properties
  {:x (s/map {:id (s/id)
              :ref-no (s/integer)
              :sold (s/boolean)})})

(defn- create-dtc [f]
  (binding [*dtc* (c/dom-type-collection :name :qwerty
                                         :properties properties)]
    (f)))

(def ^:private ^:const valid-doc
  {:x {:id (ObjectId.)
       :ref-no 123
       :sold true}
   :company-id (ObjectId.)})

(defn- insert! [doc]
  (-> (p/conj! *dtc* doc)
      (a/add-stations :replace :execute [:nop (fn [_ doc] doc)])
      deref))

(use-fixtures :each create-dtc)

(deftest valid
  (is (insert! valid-doc)))

(deftest map-value-not-matching-key-type
  (testing "type mismatch within map"
    (let [invalid-doc-1 (update-in valid-doc [:x :id] str)
          invalid-doc-2 (update-in valid-doc [:x :ref-no] str)
          invalid-doc-3 (assoc-in valid-doc [:x :sold] 1)]
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
  (let [invalid-doc-1 (assoc valid-doc :x "abc")
        invalid-doc-2 (assoc valid-doc :x 123)
        invalid-doc-3 (assoc valid-doc :x false)]
    (testing "string instead of map"
      (is (thrown+? (= (get-in % [:errors :x]) "not a map")
                    (insert! invalid-doc-1))))
    (testing "integer instead of map"
      (is (thrown+? (= (get-in % [:errors :x]) "not a map")
                    (insert! invalid-doc-2))))
    (testing "boolean instead of map"
      (is (thrown+? (= (get-in % [:errors :x]) "not a map")
                    (insert! invalid-doc-3))))))
