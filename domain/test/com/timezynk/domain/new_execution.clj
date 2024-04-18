(ns com.timezynk.domain.new-execution
  (:require [clojure.test :refer [deftest is]]
            [com.timezynk.domain.core :as c]
            [com.timezynk.domain.persistence :as p]
            [com.timezynk.mongo :as mongo2]
            [spy.core :as spy]))

(deftest test1
  (with-redefs [mongo2/insert! (spy/stub)]
    (let [dt     (c/new-dom-collection :name :coll)
          to-run (p/conj! dt {})]
      (is (= com.timezynk.domain.core.MongoPlan (type to-run)))
      (is (spy/not-called? mongo2/insert!))
      @to-run
      (is (spy/called-once? mongo2/insert!)))))
