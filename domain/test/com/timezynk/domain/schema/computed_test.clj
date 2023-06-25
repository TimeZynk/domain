(ns com.timezynk.domain.schema.computed-test
  (:require [clojure.test :refer [deftest is]]
            [com.timezynk.domain.schema :as s]
            [com.timezynk.domain.utils :as u]))

(def f ^:private (constantly 42))

(deftest toplevel
  (let [dtc (u/dtc {:x (s/number :computed f)})
        out (u/insert dtc {})]
    (is (= 42 (:x out)))))

(deftest in-vector
  (let [dtc (u/dtc {:x (s/vector (s/number :computed f))})
        out (u/insert dtc {:x []})]
    (is (= [] (:x out)))))

(deftest on-vector
  (let [dtc (u/dtc {:x (s/vector (s/number) :computed (constantly [-2]))})
        out (u/insert dtc {})]
    (is (= [-2] (:x out)))))

(deftest in-map
  (let [dtc (u/dtc {:x (s/map {:y (s/number :computed f)})})
        out (u/insert dtc {:x {}})]
    (is (= 42 (get-in out [:x :y])))))

(deftest on-map
  (let [dtc (u/dtc {:x (s/map {:y (s/number)} :computed (constantly {:y -2}))})
        out (u/insert dtc {})]
    (is (= -2 (get-in out [:x :y])))))

(deftest in-optional-map
  (let [dtc (u/dtc {:x (s/map {:y (s/number :computed f)}
                              :optional? true)})
        out (u/insert dtc {})]
    (is (not (contains? out :x)))))

(deftest in-vector-of-maps
  (let [dtc (u/dtc {:x (s/vector (s/map {:y (s/number :computed f)}))})
        out (u/insert dtc {:x [{} {}]})]
    (is (= 42 (get-in out [:x 0 :y])))
    (is (= 42 (get-in out [:x 1 :y])))))

(deftest in-map-of-vectors-of-maps
  (let [dtc (u/dtc {:w (s/map {:x (s/vector (s/map {:y (s/number :computed f)}))})})
        out (u/insert dtc {:w {:x [{} {}]}})]
    (is (= 42 (get-in out [:w :x 0 :y])))
    (is (= 42 (get-in out [:w :x 1 :y])))))
