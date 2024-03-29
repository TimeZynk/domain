(ns com.timezynk.domain.pack-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [com.timezynk.domain.pack :as p]
   [com.timezynk.domain.core :as c]
   [com.timezynk.domain.schema :as s]
   [com.timezynk.useful.mongo :as um]))

(def coll
  (c/dom-type-collection
   :name :test-object
   :version 2
   :properties {:message (s/string)
                :related-id (s/id)
                :grade (s/number)}))

(deftest pack-query-no-params
  (let [q (p/pack-query coll {:domain-query-params {}})]
    (is (= {} q))))

(deftest pack-range-query
  (let [q (p/pack-query coll {:domain-query-params {:grade {:_gte_ "4"
                                                            :_lte_ "10"}}})]
    (is (= {:grade {:$gte 4 :$lte 10}} q))))

(deftest pack-id-range-query
  (let [q (p/pack-query coll {:domain-query-params {:related-id {:_gte_ "5ec25abf087fec363a7f15f2"
                                                                 :_lte_ "5ec25ac4087fec363a7f15f3"}}})]
    (is (= {:related-id {:$gte (um/object-id "5ec25abf087fec363a7f15f2")
                         :$lte (um/object-id "5ec25ac4087fec363a7f15f3")}} q))))

(deftest get-type-path-test
  (testing "Should build path when path is defined from nested map structure"
    (let [trail '(:id :to :path :some)
          result (p/get-type-path trail)]
      (is (= result [:some :properties :path :properties :to :properties :id :type]))))

  (testing "Should build path when path is defined as keyword with dot notation"
    (let [trail '(:some.path)
          result (p/get-type-path trail)]
      (is (= result [:some :properties :path :type]))))

  (testing "Should build path when path is defined with dot notation and has nested map"
    (let [trail '(:id :path.to :some)
          result (p/get-type-path trail)]
      (is (= result [:some :properties :path :properties :to :properties :id :type])))))

(deftest pack-query-nested-query
  (let [dtc {:properties
             {:some
              {:type :map
               :properties
               {:path
                {:type :map
                 :properties
                 {:to
                  {:type :map
                   :properties
                   {:id
                    {:type :object-id}}}}}}}}}]

    (testing "Should parse query with nested map structure"
      (let [request {:domain-query-params {:some {:path {:to {:id "606c2556a14152e702448f6f"}}}
                                           :company-id (um/object-id "5f08667cfc3107569d6deb79")}
                     :route-params {:company-id (um/object-id "5f08667cfc3107569d6deb79")}}
            result (p/pack-query dtc request)]
        (is (-> result :some :path :to :id (um/object-id?)))))

    (testing "Should parse query with single dot notation field"
      (let [request {:domain-query-params {:some.path.to.id "606c2556a14152e702448f6f"
                                           :company-id (um/object-id "5f08667cfc3107569d6deb79")}
                     :route-params {:company-id (um/object-id "5f08667cfc3107569d6deb79")}}
            result (p/pack-query dtc request)]
        (is (-> result :some.path.to.id (um/object-id?)))))
    (testing "Should parse query with nested map structure include dot notation field"
      (let [request {:domain-query-params {:some {:path.to {:id "606c2556a14152e702448f6f"}}
                                           :company-id (um/object-id "5f08667cfc3107569d6deb79")}
                     :route-params {:company-id (um/object-id "5f08667cfc3107569d6deb79")}}
            result (p/pack-query dtc request)]
        (is (-> result :some :path.to :id (um/object-id?)))))))

(deftest pack-doc-test
  (testing "Should pack malformed ObjectId values"
    (let [packed (p/pack-doc coll {:message "hi"
                                   :related-id "not-an-objectid"
                                   :grade 10})]
      (is (= "not-an-objectid" (:related-id packed))))))
