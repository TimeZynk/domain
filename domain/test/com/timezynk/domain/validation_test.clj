(ns com.timezynk.domain.validation-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [com.timezynk.domain.validation :as v])
  (:import [org.joda.time LocalDate]))

(deftest function-calls
  (testing "Functions are called correctly"
    (let [func (v/all-of (v/lt= :start :end)
                         (v/eq :start :end))]
      (is (= [true {}]
             (func {:start (LocalDate. "2010-01-02")
                    :end (LocalDate. "2010-01-02")})))))
  (testing "Functions are called correctly with java.time"
    (let [func (v/all-of (v/lt= :start :end)
                         (v/eq :start :end))]
      (is (= [true {}]
             (func {:start (java.time.LocalDate/parse "2010-01-02")
                    :end (java.time.LocalDate/parse "2010-01-02")}))))))

