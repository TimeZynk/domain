(ns com.timezynk.domain.schema.default-test
  (:require [clojure.test :refer [deftest is]]
            [com.timezynk.domain.schema :as s]
            [com.timezynk.domain.utils :as u])
  (:import [org.bson.types ObjectId]))

(deftest constant
  (let [dtc (u/dom-type-collection :properties {:x (s/number :default -2)})
        out (u/insert dtc {:company-id (ObjectId.)})]
    (is (= -2 (:x out)))))

(deftest function
  (let [f (constantly 42)
        dtc (u/dom-type-collection :properties {:x (s/number :default f)})
        out (u/insert dtc {:company-id (ObjectId.)})]
    (is (= 42 (:x out)))))
