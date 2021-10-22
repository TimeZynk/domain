(ns com.timezynk.domain.validation.set
  (:require
   [clojure.set :as set]
   [clojure.test :refer [deftest is testing]]
   [com.timezynk.domain.validation.validation-operator :refer [validation-operator]]
   [validateur.validation :as v]))

(defn all-of
  "All conditions should be true (AND)"
  [& rules]
  (validation-operator (fn [results] (reduce #(and % %2) results))
                       (fn [errs] (apply set/union errs))
                       rules))

(defn- some-of-proto [treshold-func err-wrap-name rules]
  (validation-operator (fn [results] (->> results
                                          (filter true?)
                                          count
                                          treshold-func))
                       (fn [errs] {err-wrap-name (apply set/union errs)})
                       rules))

(defn some-of
  "At least one condition should be true (OR)"
  [& rules]
  (some-of-proto pos? :or rules))

(defn none-of
  "No condition should be true"
  [& rules]
  (some-of-proto zero? :none rules))

(defn one-of
  "One condition should be true (XOR)"
  [& rules]
  (some-of-proto #(= 1 %) :xor rules))

(defn has [& properties]
  (fn [entry]
    (let [rule (->> properties
                    (map #(v/presence-of %))
                    (apply all-of))]
      (rule entry))))

(defn- no-presence-of [attribute]
  (fn [entry]
    (let [[presence-of?] ((v/presence-of attribute) entry)]
      (if presence-of?
        [false {attribute #{"have to be blank"}}]
        [true {attribute #{}}]))))

(defn has-not [& attributes]
  (fn [entry]
    (let [rule (->> attributes
                    (map #(no-presence-of %))
                    (apply all-of))]
      (rule entry))))

(deftest test-sets
  (let [func1 (fn [_] [true {}])
        func2 (fn [_] [true {}])
        func3 (fn [_] [false #{"This is an error"}])
        func4 (fn [_] [false #{"This is also an error"}])]
    (testing "all-of"
      (is (= [true {}]
             ((all-of func1 func2) "")))
      (is (= [false #{"This is an error"}]
             ((all-of func1 func2 func3) ""))))
    (testing "some-of"
      (is (= [true {}]
             ((some-of func1 func3) "")))
      (is (= [false {:or #{"This is an error" "This is also an error"}}]
             ((some-of func3 func4) "")))
      (is (= [true {}]
             ((some-of func4 func3 func2) ""))))
    (testing "none-of"
      (is (= [true {}]
             ((none-of func3 func4) "")))
      (is (= [false {:none #{"This is also an error"}}]
             ((none-of func1 func2 func4) ""))))
    (testing "one-of"
      (is (= [true {}]
             ((one-of func2 func3 func4) "")))
      (is (= [false {:xor #{"This is an error" "This is also an error"}}]
             ((one-of func1 func2 func3 func4) ""))))))

(deftest test-fields
  (testing "has"
    (is (= [true {}]
           ((has :field1 :field2) {:field1 ""
                                   :field2 ""})))
    (is (= [false {:field1 #{"can't be blank"}
                   :field2 #{"can't be blank"}}]
           ((has :field1 :field2) {:field1 nil}))))
  (testing "has-not"
    (is (= [true {}]
           ((has-not :field1 :field2) {})))
    (is (= [false {:field1 #{"have to be blank"}}]
           ((has-not :field1) {:field1 ""
                               :field2 nil})))))
