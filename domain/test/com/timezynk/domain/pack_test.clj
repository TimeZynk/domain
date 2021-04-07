(ns com.timezynk.domain.pack-test
  (:require [clojure.test :refer :all]
            [clojure.tools.logging :as log]
            [com.timezynk.domain.pack :as p]))

(deftest test-1
  (testing "Should build path when path is defined as keyword with dot notation"
    (let [trail '(:relations.outgoing-rfq-id)
          result (p/get-type-path trail)]
      (is (= result [:relations :properties :outgoing-rfq-id :type])))))
