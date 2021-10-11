(ns com.timezynk.domain.validation.only-these
  (:require
   [clojure.set :refer [difference]]
   [clojure.test :refer [deftest is]]))

(defn only-these
  "only these are valid attributes"
  [& valid-attributes]
  (fn [entry]
    (let [invalid-attrs (when-not (nil? valid-attributes)
                          (-> (keys entry)
                              set
                              (difference (into #{} valid-attributes))))]
      (if (empty? invalid-attrs)
        [true {}]
        [false (apply merge (map (fn [attr] {attr "invalid attribute"}) invalid-attrs))]))))

(deftest test-only-these
  (is (= [true {}]
         ((only-these :field1 :field2) {:field1 ""
                                        :field2 ""})))
  (is (= [false {:field3 "invalid attribute"}]
         ((only-these :field1 :field2) {:field1 ""
                                        :field3 ""}))))
