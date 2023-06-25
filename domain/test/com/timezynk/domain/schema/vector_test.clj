(ns com.timezynk.domain.schema.vector-test
  (:require [clojure.test :refer [deftest is testing]]
            [slingshot.test]
            [com.timezynk.domain.schema :as s]
            [com.timezynk.domain.utils :as u]))

(def ^:private dtc
  (u/dtc {:x (s/vector (s/integer))}))

(def ^:private ^:const valid-doc
  {:x [1 2 3]})

(deftest valid
  (is (u/insert dtc valid-doc)))

(deftest vector-value-not-matching-item-type
  (testing "type mismatch within vector"
    (let [invalid-doc-1 (update-in valid-doc [:x 0] str)
          invalid-doc-2 (update-in valid-doc [:x 1] boolean)
          invalid-doc-3 (update-in valid-doc [:x 2] #(hash-map :x %))]
      (testing "string instead of integer"
        (is (thrown+? (= (get-in % [:errors :x 0]) "not an integer")
                      (u/insert dtc invalid-doc-1))))
      (testing "boolean instead of integer"
        (is (thrown+? (= (get-in % [:errors :x 1]) "not an integer")
                      (u/insert dtc invalid-doc-2))))
      (testing "map instead of integer"
        (is (thrown+? (= (get-in % [:errors :x 2]) "not an integer")
                      (u/insert dtc invalid-doc-3)))))))

(deftest non-vector-instead-of-vector
  (let [invalid-doc-1 (assoc valid-doc :x "abc")
        invalid-doc-2 (assoc valid-doc :x 123)
        invalid-doc-3 (assoc valid-doc :x false)]
    (testing "string instead of map"
      (is (thrown+? (= (get-in % [:errors :x]) {:vector "not sequential"})
                    (u/insert dtc invalid-doc-1))))
    (testing "integer instead of map"
      (is (thrown+? (= (get-in % [:errors :x]) {:vector "not sequential"})
                    (u/insert dtc invalid-doc-2))))
    (testing "boolean instead of map"
      (is (thrown+? (= (get-in % [:errors :x]) {:vector "not sequential"})
                    (u/insert dtc invalid-doc-3))))))
