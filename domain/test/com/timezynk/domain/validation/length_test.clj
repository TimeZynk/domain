(ns com.timezynk.domain.validation.length-test
  (:require [clojure.test :refer [deftest is testing]]
            [com.timezynk.domain.validation.validate :refer [validate-schema]]))

(defn- build-validator [spec]
  (fn [value]
    (validate-schema false
                     {:properties {:field spec}}
                     {:field value})))

(deftest test-min-length
  (testing "expecting string"
    (let [f (build-validator {:type :string :min-length 3})]
      (testing "string input"
        (is (= [false {:field "shorter than 3"}]
               (f "12"))))
      (testing "integer input"
        (is (= [false {:field "not a string"}]
               (f 12))))))

  (testing "expecting vector"
    (let [f (build-validator {:type :vector :min-length 3})]
      (testing "vector input"
        (is (= [false {:field "shorter than 3"}]
               (f [1 2]))))
      (testing "string input"
        (is (= [false {:field "not sequential"}]
               (f "12"))))))

  (testing "expecting map"
    (let [f (build-validator {:type :map :min-length 3})]
      (testing "map input"
        (is (= [false {:field "shorter than 3"}]
               (f {:x 1 :y 2}))))
      (testing "string input"
        (is (= [false {:field "not a map"}]
               (f "12")))))))

(deftest test-max-length
  (testing "expecting string"
    (let [f (build-validator {:type :string :max-length 2})]
      (testing "string input"
        (is (= [false {:field "longer than 2"}]
               (f "123"))))
      (testing "integer input"
        (is (= [false {:field "not a string"}]
               (f 123))))))

  (testing "expecting vector"
    (let [f (build-validator {:type :vector :max-length 2})]
      (testing "vector input"
        (is (= [false {:field "longer than 2"}]
               (f [1 2 3]))))
      (testing "string input"
        (is (= [false {:field "not sequential"}]
               (f "123"))))))

  (testing "expecting map"
    (let [f (build-validator {:type :map :max-length 2})]
      (testing "map input"
        (is (= [false {:field "longer than 2"}]
               (f {:x 1 :y 2 :z 3}))))
      (testing "string input"
        (is (= [false {:field "not a map"}]
               (f "123")))))))
