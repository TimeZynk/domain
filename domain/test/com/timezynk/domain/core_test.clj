(ns com.timezynk.domain.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [com.timezynk.domain.core :as c]
            [com.timezynk.domain.schema :as s]
            [com.timezynk.mongo.convert-types :refer [clj->doc doc->clj]])
  (:import [org.bson Document]))

(deftest define-new-dom-type
  (testing "Defining new DOM Type"
    (let [dtc (c/dom-type-collection
               :name :test-object
               :version 2
               :properties {:message (s/string)})]
      (is (not (nil? dtc)))
      (is (= :test.object2 (c/collection-name dtc)))
      (is (= :string (get-in dtc [:properties :message :type]))))))

(deftest new-dom-convert-ids
  (c/convert-ids
   (let [doc (-> (Document.)
                 (.append "_name" 1)
                 (.append "_id" 3)
                 (.append "f" (-> (Document.)
                                  (.append "_name" 2))))
         clj {:id 1
              :vid 3
              :f {:id 2}}]
     (is (= doc (clj->doc clj)))
     (is (= clj (doc->clj doc))))))
