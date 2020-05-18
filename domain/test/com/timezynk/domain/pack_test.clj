(ns com.timezynk.domain.pack-test
  (:require
   [clojure.test :refer :all]
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
