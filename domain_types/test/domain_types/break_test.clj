(ns domain-types.break-test
  (:require [clojure.test :refer [deftest is testing]]
            [com.timezynk.domain-types.breaks :as b]
            [com.timezynk.useful.date :as date]))

(deftest validate-single
  (testing "Single break"
    (let [doc {:start (date/to-datetime "2019-12-17T08:00:00")
               :end (date/to-datetime "2019-12-17T19:00:00")
               :breaks [{:start (date/to-datetime "2019-12-17T12:00:00")
                         :end (date/to-datetime "2019-12-17T13:00:00")}]}]
      (is (= doc (b/validate-breaks! nil doc))))))

(deftest validate-double
  (testing "Double break"
    (let [doc {:start (date/to-datetime "2019-12-17T08:00:00")
               :end (date/to-datetime "2019-12-17T19:00:00")
               :breaks [{:start (date/to-datetime "2019-12-17T12:00:00")
                         :end (date/to-datetime "2019-12-17T13:00:00")}
                        {:start (date/to-datetime "2019-12-17T14:00:00")
                         :end (date/to-datetime "2019-12-17T14:30:00")}]}]
      (is (= doc (b/validate-breaks! nil doc))))))
