(ns com.timezynk.domain.schema.walk.update-test
  (:require [clojure.string :as string]
            [clojure.test :refer [deftest is]]
            [com.timezynk.domain.schema :as s]
            [com.timezynk.domain.schema.walk :refer [update-properties]]))

(defn- update-fn
  [parent k spec]
  (cond-> parent
    (= :string (:type spec)) (update k string/upper-case)))

(deftest toplevel
  (let [schema {:x (s/string)}
        in {:x "abc"}
        out (update-properties in schema update-fn)]
    (is (= "ABC" (:x out)))))

(deftest in-vector
  (let [schema {:x (s/vector (s/string))}
        in {:x ["abc" "xyz"]}
        out (update-properties in schema update-fn)]
    (is (= "ABC" (get-in out [:x 0])))
    (is (= "XYZ" (get-in out [:x 1])))))

(deftest in-map
  (let [schema {:x (s/map {:y (s/string)})}
        in {:x {:y "abc"}}
        out (update-properties in schema update-fn)]
    (is (= "ABC" (get-in out [:x :y])))))

(deftest in-vector-of-maps
  (let [schema {:x (s/vector (s/map {:y (s/string)}))}
        in {:x [{:y "abc"}
                {:y "xyz"}]}
        out (update-properties in schema update-fn)]
    (is (= "ABC" (get-in out [:x 0 :y])))
    (is (= "XYZ" (get-in out [:x 1 :y])))))

(deftest in-map-of-vectors-of-maps
  (let [schema {:x (s/map {:y (s/vector (s/map {:z (s/string)}))})}
        in {:x {:y [{:z "abc"}
                    {:z "xyz"}]}}
        out (update-properties in schema update-fn)]
    (is (= "ABC" (get-in out [:x :y 0 :z])))
    (is (= "XYZ" (get-in out [:x :y 1 :z])))))
