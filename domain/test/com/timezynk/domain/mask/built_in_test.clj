(ns com.timezynk.domain.mask.built-in-test
  (:require [clojure.test :refer [deftest is testing compose-fixtures use-fixtures]]
            [spy.core :refer [stub spy called-n-times? call-matching?]]
            [com.timezynk.domain.core :as c]
            [com.timezynk.domain.mask.built-in :refer [unauthorized?]]
            [com.timezynk.domain.schema :as s]
            [com.timezynk.domain.mongo.core :as m]
            [com.timezynk.useful.cancan :as ability]
            [com.timezynk.domain.persistence :as p]
            [com.timezynk.domain.utils :as u])
  (:import [org.bson.types ObjectId]))

(def ^:dynamic *dtc*)

(def ^:const dtc-properties
  {:x (s/string)
   :y (s/string :mask unauthorized?)
   :z (s/map {:z1 (s/string)
              :z2 (s/string :mask unauthorized?)
              :z3 (s/string)})})

(def ^:const records
  [{:x "abc"
    :y "123"
    :z {:z1 "xyz-1"
        :z2 "xyz-2"
        :z3 "xyz-3"}}])

(defn- create-dtc [f]
  (binding [*dtc* (c/dom-type-collection :name :qwerty
                                         :properties dtc-properties)]
    (f)))

(defn- with-authorization-failure [f]
  (ability/with-ability
    (with-redefs [ability/can? (stub false)]
      (f))))

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
      (testing "marked nested property"
        (is (not (contains? (:z doc) :z2))))
      (testing "unmarked nested properties"
        (is (contains? (:z doc) :z1))
        (is (contains? (:z doc) :z3)))
      (testing "authorization"
        (testing "times called"
          (is (called-n-times? ability/can? 2)))
        (testing "top-level property action"
          (is (call-matching? ability/can? (comp #{:read-property-y} first))))
        (testing "nested property action"
          (is (call-matching? ability/can? (comp #{:read-property-z.z2} first))))
        (testing "object"
          (is (call-matching? ability/can? (comp #{:qwerty} second))))))))

(defn- mask-matcher
  "Builds a predicate suitable for spy.core/call-matching? which checks whether
   a (potentially nested) property is present in the incoming document."
  [masked? property-path]
  (let [prefix (pop property-path)
        property (peek property-path)]
    (fn [doc]
      (cond-> doc
        (not-empty prefix) (get-in prefix)
        true (contains? property)
        masked? (not)))))

(deftest creating
  (let [args->doc #(->> % last (into []) (peek))
        masked #(comp (mask-matcher true %) args->doc)
        not-masked #(comp (mask-matcher false %) args->doc)]
    (testing "Adding to the store"
      @(p/conj! *dtc* {:x "cba"
                       :y "321"
                       :z {:z1 "zyx-1"
                           :z2 "zyx-2"
                           :z3 "zyx-3"}
                       :company-id (ObjectId.)})
      (testing "marked property"
        (is (call-matching? m/insert! (masked [:y]))))
      (testing "unmarked properties"
        (is (call-matching? m/insert! (not-masked [:x])))
        (is (call-matching? m/insert! (not-masked [:z]))))
      (testing "marked nested property"
        (is (call-matching? m/insert! (masked [:z :z2]))))
      (testing "unmarked nested properties"
        (is (call-matching? m/insert! (not-masked [:z :z1])))
        (is (call-matching? m/insert! (not-masked [:z :z3])))))))

(deftest updating
  (let [masked #(comp (mask-matcher true %) last)
        not-masked #(comp (mask-matcher false %) last)]
    (testing "Updating the store"
      @(p/update-in! *dtc* {} {:x "cba"
                               :y "321"
                               :z {:z1 "zyx-1"
                                   :z2 "zyx-2"
                                   :z3 "zyx-3"}})
      (testing "marked property"
        (is (call-matching? m/update! (masked [:y]))))
      (testing "unmarked properties"
        (is (call-matching? m/update! (not-masked [:x])))
        (is (call-matching? m/update! (not-masked [:z]))))
      (testing "marked nested property"
        (is (call-matching? m/update! (masked [:z :z2]))))
      (testing "unmarked nested properties"
        (is (call-matching? m/update! (not-masked [:z :z1])))
        (is (call-matching? m/update! (not-masked [:z :z3])))))))
