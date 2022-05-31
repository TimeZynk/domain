(ns com.timezynk.domain.dtc.masks-test
  (:require [clojure.test :refer [deftest is testing compose-fixtures use-fixtures]]
            [spy.core :refer [stub spy called-once? call-matching?]]
            [com.timezynk.domain.core :as c]
            [com.timezynk.domain.dtc.masks :refer [authorization]]
            [com.timezynk.domain.schema :as s]
            [com.timezynk.domain.mongo.core :as m]
            [com.timezynk.useful.cancan :as ability]
            [com.timezynk.domain.persistence :as p]
            [com.timezynk.domain.utils :as u])
  (:import [org.bson.types ObjectId]))

(def ^:dynamic *dtc*)

(def ^:const dtc-properties
  {:x (s/string)
   :y (s/string :mask authorization)
   :z (s/string)})

(def ^:const records
  [{:x "abc" :y "123" :z "xyz"}])

(defn- create-dtc [f]
  (binding [*dtc* (c/dom-type-collection :name :qwerty
                                         :properties dtc-properties)]
    (f)))

(defn- with-authorization-failure [f]
  (with-redefs [ability/can? (stub false)]
    (f)))

(use-fixtures :each (->> create-dtc
                         (compose-fixtures (u/build-immutable-inmemory-store records))
                         (compose-fixtures with-authorization-failure)))

(deftest reading
  (testing "Reading from the store"
    (let [result @(p/select *dtc*)
          doc (first result)]
      (testing "marked property"
        (is (not (contains? doc :y))))
      (testing "unmarked properties"
        (is (contains? doc :x))
        (is (contains? doc :z)))
      (testing "authorization"
        (testing "times called"
          (is (called-once? ability/can?)))
        (testing "action"
          (is (call-matching? ability/can? (comp #{:read-property-y} first))))
        (testing "object"
          (is (call-matching? ability/can? (comp #{:qwerty} second))))))))

(deftest creating
  (testing "Adding to the store"
    @(p/conj! *dtc* {:x "cba" :y "321" :z "zyx" :company-id (ObjectId.)})
    (testing "marked property"
      (is (call-matching? m/insert! (fn [[_ new-doc]]
                                      (let [new-doc (peek (into [] new-doc))]
                                        (not (contains? new-doc :y)))))))
    (testing "unmarked property"
      (is (call-matching? m/insert! (fn [[_ new-doc]]
                                      (let [new-doc (peek (into [] new-doc))]
                                        (contains? new-doc :z))))))))

(deftest updating
  (testing "Updating the store"
    @(p/update-in! *dtc* {} {:y "321" :z "zyx"})
    (testing "marked property"
      (is (call-matching? m/update! (fn [[_ _ new-doc]]
                                      (not (contains? new-doc :y))))))
    (testing "unmarked property"
      (is (call-matching? m/update! (fn [[_ _ new-doc]]
                                      (contains? new-doc :z)))))))
