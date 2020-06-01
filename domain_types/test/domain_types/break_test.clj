(ns domain-types.break-test
  (:require [clojure.test :refer :all]
            [com.timezynk.domain-types.breaks :as b]
            [com.timezynk.useful.date :as ud]))

(deftest validate-single
  (testing "Single break"
    (let [doc {:start (ud/->local-datetime "2019-12-17T08:00:00")
               :end (ud/->local-datetime "2019-12-17T19:00:00")
               :breaks [{:start (ud/->local-datetime "2019-12-17T12:00:00")
                         :end (ud/->local-datetime "2019-12-17T13:00:00")}]}]
      (is (= doc (b/validate-breaks! nil doc))))))

(deftest validate-double
  (testing "Double break"
    (let [doc {:start (ud/->local-datetime "2019-12-17T08:00:00")
               :end (ud/->local-datetime "2019-12-17T19:00:00")
               :breaks [{:start (ud/->local-datetime "2019-12-17T12:00:00")
                         :end (ud/->local-datetime "2019-12-17T13:00:00")}
                        {:start (ud/->local-datetime "2019-12-17T14:00:00")
                         :end (ud/->local-datetime "2019-12-17T14:30:00")}]}]
      (is (= doc (b/validate-breaks! nil doc))))))
