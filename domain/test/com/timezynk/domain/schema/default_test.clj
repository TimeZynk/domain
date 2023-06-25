(ns com.timezynk.domain.schema.default-test
  (:require [clojure.test :refer [deftest is]]
            [com.timezynk.domain.schema :as s]
            [com.timezynk.domain.utils :as u]))

(def f ^:private (constantly 42))

(deftest toplevel
  (let [dtc (u/dtc {:x (s/number :default -2)
                    :y (s/number :default f)})
        out (u/insert dtc {})]
    (is (= -2 (:x out)))
    (is (= 42 (:y out)))))

(deftest in-vector
  (let [dtc (u/dtc {:x (s/vector (s/number :default -2))
                    :y (s/vector (s/number :default f))})
        out (u/insert dtc {:x [] :y []})]
    (is (= [] (:x out)))
    (is (= [] (:y out)))))

(deftest on-vector
  (let [dtc (u/dtc {:x (s/vector (s/number) :default [-2])
                    :y (s/vector (s/number) :default [])})
        out (u/insert dtc {})]
    (is (= [-2] (:x out)))
    (is (= [] (:y out)))))

(deftest in-map
  (let [dtc (u/dtc {:x (s/map {:y (s/number :default -2)
                               :z (s/number :default f)})})
        out (u/insert dtc {:x {}})]
    (is (= -2 (get-in out [:x :y])))
    (is (= 42 (get-in out [:x :z])))))

(deftest on-map
  (let [dtc (u/dtc {:x (s/map {:w (s/number)} :default {:w -2})
                    :y (s/map {:z (s/number)} :default {:z 42})})
        out (u/insert dtc {})]
    (is (= -2 (get-in out [:x :w])))
    (is (= 42 (get-in out [:y :z])))))

(deftest in-optional-map
  (let [dtc (u/dtc {:x (s/map {:y (s/number :default -2)
                               :z (s/number :default f)}
                              :optional? true)})
        out (u/insert dtc {})]
    (is (not (contains? out :x)))))

(deftest in-vector-of-maps
  (let [dtc (u/dtc {:x (s/vector (s/map {:y (s/number :default -2)
                                         :z (s/number :default f)}))})
        out (u/insert dtc {:x [{} {}]})]
    (is (= -2 (get-in out [:x 0 :y])))
    (is (= 42 (get-in out [:x 0 :z])))
    (is (= -2 (get-in out [:x 1 :y])))
    (is (= 42 (get-in out [:x 1 :z])))))

(deftest in-map-of-vectors-of-maps
  (let [dtc (u/dtc {:w (s/map {:x (s/vector (s/map {:y (s/number :default -2)
                                                    :z (s/number :default f)}))})})
        out (u/insert dtc {:w {:x [{} {}]}})]
    (is (= -2 (get-in out [:w :x 0 :y])))
    (is (= 42 (get-in out [:w :x 0 :z])))
    (is (= -2 (get-in out [:w :x 1 :y])))
    (is (= 42 (get-in out [:w :x 1 :z])))))
