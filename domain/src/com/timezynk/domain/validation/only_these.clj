(ns com.timezynk.domain.validation.only-these
  (:require
   [clojure.set :refer [difference]]
   [clojure.test :refer [deftest is]]))

(defn- only-these* [entry valid-attributes]
  (let [invalid-attrs (when-not (nil? valid-attributes)
                        (-> (keys entry)
                            set
                            (difference (into #{} valid-attributes))))]
    [(empty? invalid-attrs)
     (reduce #(assoc %1 %2 "invalid attribute") {} invalid-attrs)]))

(defn only-these
  "only these are valid attributes"
  [& valid-attributes]
  (fn [entry]
    (if (map? entry)
      (only-these* entry valid-attributes)
      [true {}])))

(deftest test-only-these
  (is (= [true {}]
         ((only-these :field1 :field2) {:field1 ""
                                        :field2 ""})))
  (is (= [false {:field3 "invalid attribute"}]
         ((only-these :field1 :field2) {:field1 ""
                                        :field3 ""})))
  (is (= [true {}]
         ((only-these :field1 :field2) 123))))
