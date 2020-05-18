(ns com.timezynk.domain.middleware-test
  (:require
   [clojure.test :refer :all]
   [com.timezynk.domain.middleware :as m]
   [com.timezynk.domain.core :as c]
   [com.timezynk.domain.schema :as s]))

(def coll
  (c/dom-type-collection
   :name :test-object
   :version 2
   :properties {:message (s/string)
                :related-id (s/id)}))

(deftest parse-simple-query-param
  (let [[query] (m/parse-params {:query-params {"approved-id" "ABC"}})]
    (is (= {:approved-id "ABC"} query))))

(deftest parse-two-simple-query-params
  (let [[query] (m/parse-params {:query-params {"approved-id" "ABC"
                                                "user-id" "u123"}})]
    (is (= {:approved-id "ABC"
            :user-id "u123"} query))))

(deftest parse-operator-query-param
  (let [[query] (m/parse-params {:query-params {"approved-id[ne]" "ABC"}})]
    (is (= {:approved-id {:_ne_ "ABC"}} query))))

(deftest parse-operator-two-query-params
  (let [[query] (m/parse-params {:query-params {"approved-id[gte]" "4"
                                                "approved-id[lte]" "10"}})]
    (is (= {:approved-id {:_gte_ "4"
                          :_lte_ "10"}} query))))
