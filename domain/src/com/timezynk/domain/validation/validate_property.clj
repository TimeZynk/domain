(ns com.timezynk.domain.validation.validate-property
  (:require
   [clojure.core.reducers :as r]
   [clojure.test :refer [deftest is testing]]
   [clojure.tools.logging :as log]
   [com.timezynk.domain.validation.check :refer [check]]
   [com.timezynk.domain.validation.validate-type :refer [validate-type]]
   [com.timezynk.domain.validation.operator.all-of :refer [all-of]]
   [spy.core :as spy]))

(declare validate-schema)

(defn- validate-vector [all-optional? attr-name rule vector-value]
  (if (sequential? vector-value)
    (let [[valid? errors] (r/reduce
                           (fn [acc v]
                             (let [[v? e] (validate-schema all-optional? rule v)]
                               [(and (first acc) v?)
                                (merge (second acc) e)]))
                           [true {}]
                           vector-value)]
      [valid? {attr-name errors}])
    [false {attr-name {:vector "not sequential"}}]))

(defn- get-check-fn [all-optional? k property-name property-definition]
  (case k
    :min        (check #(<= property-definition %)
                       property-name
                       (str "smaller than " property-definition))
    :max        (check #(>= property-definition %)
                       property-name
                       (str "bigger than " property-definition))
    :type       (validate-type property-name property-definition)
    ;;:vector    (partial validate-vector property-name property-definition)
    :properties #(validate-schema all-optional? {:properties property-definition} %)
    ;;:children   (fn [v] (map #(#'validate-schema property-definition %) v))
    :children   (partial validate-vector all-optional? property-name property-definition)
    ;;:map        property-definition
    :in         (check #(contains? property-definition %)
                       property-name
                       (str "not in " property-definition))))

;; todo: this is UGLY! this should not be done here...
;; updated 2021-09-27: better handling of null values during update
(defn- escape-optional? [property property-value all-optional?]
  (log/spy "ESCAPE-OPTIONAL")
  (let [[property-name property-definition] property]
    (or (and all-optional?
             (not (contains? property-value property-name)))
        (and (or (:optional? property-definition)
                 (:default property-definition))
             (nil? (property-name property-value)))
        (:computed property-definition)
        (:derived property-definition))))

(def validation-keys [:min
                      :max
                      :type
                      :properties
                      :children
                      :in])

(defn validate-property [property all-optional? val]
  (if (escape-optional? property val all-optional?)
    [true {}]
    (let [[property-name property-definition] property
          property-definition                 (select-keys property-definition validation-keys)
          property-value                      (get val property-name)
          validator                           (->> (dissoc property-definition :optional?)
                                                   (map (fn [[k v]]
                                                          (get-check-fn all-optional? k property-name v)))
                                                   (apply all-of))]
      (validator property-value))))

(deftest test-escape-optional?
  (testing "A required field that is missing at creation should be checked"
    (is (not (escape-optional? [:field {}] {} false))))
  (testing "A required field that is missing at update should not be checked"
    (is (escape-optional? [:field {}] {} true)))
  (testing "A required field set to null or value should always be checked"
    (is (not (escape-optional? [:field {}] {:field nil} false)))
    (is (not (escape-optional? [:field {}] {:field nil} true)))
    (is (not (escape-optional? [:field {}] {:field 1} false)))
    (is (not (escape-optional? [:field {}] {:field 1} true))))
  (testing "An optional or default field that is missing or set to null should never be checked"
    (is (escape-optional? [:field {:optional? true}] {} false))
    (is (escape-optional? [:field {:optional? true}] {} true))
    (is (escape-optional? [:field {:optional? true}] {:field nil} false))
    (is (escape-optional? [:field {:optional? true}] {:field nil} true))
    (is (escape-optional? [:field {:default 1}] {} false))
    (is (escape-optional? [:field {:default 1}] {} true))
    (is (escape-optional? [:field {:default 1}] {:field nil} false))
    (is (escape-optional? [:field {:default 1}] {:field nil} true)))
  (testing "An optional or default field set to value should always be checked"
    (is (not (escape-optional? [:field {:optional? true}] {:field 1} false)))
    (is (not (escape-optional? [:field {:optional? true}] {:field 1} true)))
    (is (not (escape-optional? [:field {:default 1}] {:field 1} false)))
    (is (not (escape-optional? [:field {:default 1}] {:field 1} true))))
  (testing "A computed or derived field should never be checked"
    (is (escape-optional? [:field {:computed true}] {} false))
    (is (escape-optional? [:field {:computed true}] {} true))
    (is (escape-optional? [:field {:computed true}] {:field nil} false))
    (is (escape-optional? [:field {:computed true}] {:field nil} true))
    (is (escape-optional? [:field {:computed true}] {:field 1} false))
    (is (escape-optional? [:field {:computed true}] {:field 1} true))
    (is (escape-optional? [:field {:derived true}] {} false))
    (is (escape-optional? [:field {:derived true}] {} true))
    (is (escape-optional? [:field {:derived true}] {:field nil} false))
    (is (escape-optional? [:field {:derived true}] {:field nil} true))
    (is (escape-optional? [:field {:derived true}] {:field 1} false))
    (is (escape-optional? [:field {:derived true}] {:field 1} true))))

(deftest test-validate-property
  (is (= [true {}]
         (validate-property [:field {:type :string}]
                            false
                            {:field "123"})))
  (is (= [false {:field "required property has no value"}]
         (validate-property [:field {:type :date-time}]
                            false
                            {:field nil})))
  (is (= [false {:field "not a valid date-time declaration"}]
         (validate-property [:field {:type :date-time}]
                            false
                            {:field "abc"}))))
