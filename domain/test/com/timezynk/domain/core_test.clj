(ns com.timezynk.domain.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [com.timezynk.domain.core :as c]
            [com.timezynk.domain.schema :as s]))

(deftest define-new-dom-type
  (testing "Defining new DOM Type"
    (let [dtc (c/dom-type-collection
               :name :test-object
               :version 2
               :properties {:message (s/string)})]
      (is (not (nil? dtc)))
      (is (= :test.object2 (c/collection-name dtc)))
      (is (= :string (get-in dtc [:properties :message :type]))))))
