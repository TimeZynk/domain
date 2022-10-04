(ns com.timezynk.domain.mask-test
  (:require [clojure.test :refer [deftest is testing]]
            [spy.core :as spy]
            [com.timezynk.domain.core :refer [dom-type-collection]]
            [com.timezynk.domain.mask.utils :as mu]
            [com.timezynk.domain.mongo.core :as m]
            [com.timezynk.domain.persistence :as p]
            [com.timezynk.domain.schema :as s]
            [com.timezynk.domain.utils :as u])
  (:import [org.bson.types ObjectId]))

(deftest fetch
  (let [f (spy/stub true)
        dtc (dom-type-collection :name :qwerty
                                 :properties {:x (s/string :mask f)
                                              :y (s/string)})
        original-doc {:x "abc" :y "123"}
        result-doc (with-redefs [m/fetch (spy/stub [original-doc])]
                     (p/->1 dtc p/select))]
    (testing "property presence"
      (is (not (contains? result-doc :x)))
      (is (contains? result-doc :y)))
    (testing "masking function"
      (testing "calls"
        (is (spy/called-once? f)))
      (testing "action"
        (is (spy/call-matching? f (mu/action-matcher :read))))
      (testing "property-name"
        (is (spy/call-matching? f (mu/property-name-matcher "x")))))))

(deftest fetch-nested
  (let [f (spy/stub true)
        dtc (dom-type-collection :name :qwerty
                                 :properties {:x (s/map {:z (s/string :mask f)})
                                              :y (s/string)})
        original-doc {:x {:z "123"} :y "abc"}
        result-doc (with-redefs [m/fetch (spy/stub [original-doc])]
                     (p/->1 dtc p/select))]
    (testing "property presence"
      (is (contains? result-doc :x))
      (is (not (contains? (:x result-doc) :z))))
    (testing "masking function property-name"
      (is (spy/call-matching? f (mu/property-name-matcher "x.z"))))))

(deftest create
  (let [f (spy/stub true)
        dtc (dom-type-collection :name :qwerty
                                 :properties {:x (s/string :mask f)
                                              :y (s/string)})
        original-doc {:x "123" :y "abc" :company-id (ObjectId.)}
        result-doc (with-redefs [m/insert! (spy/spy (fn [_ docs]
                                                      (into [] docs)))]
                     (p/->1 dtc (p/conj! original-doc)))]
    (testing "property presence"
      (is (not (contains? result-doc :x)))
      (is (contains? result-doc :y)))
    (testing "masking function"
      (testing "calls"
        (is (spy/called-once? f)))
      (testing "action"
        (is (spy/call-matching? f (mu/action-matcher :create))))
      (testing "property-name"
        (is (spy/call-matching? f (mu/property-name-matcher "x")))))))

(deftest create-with-default
  (let [f (spy/stub true)
        dtc (dom-type-collection :name :qwerty
                                 :properties {:x (s/string :mask f
                                                           :default "!")})
        original-doc {:x "123" :company-id (ObjectId.)}
        result-doc (with-redefs [m/insert! (spy/spy (fn [_ docs]
                                                      (into [] docs)))]
                     (p/->1 dtc (p/conj! original-doc)))]
    (is (= "!" (:x result-doc)))))
