(ns com.timezynk.domain.validation.validate
  (:require
   [clojure.core.reducers :as r]
   [clojure.test :refer [deftest is testing]]
   [com.timezynk.domain.validation.check :refer [check]]
   [com.timezynk.domain.validation.only-these :refer [only-these]]
   [com.timezynk.domain.validation.set :refer [all-of]]
   [com.timezynk.domain.validation.validate-type :refer [validate-type]]
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

(def ^:private check-by-length?
  (some-fn string? vector? map?))

(defn- get-check-fn [all-optional? k property-name property-definition]
  (case k
    :min        (check #(<= property-definition %)
                       property-name
                       (str "smaller than " property-definition))
    :max        (check #(>= property-definition %)
                       property-name
                       (str "bigger than " property-definition))
    :min-length (check #(and (check-by-length? %)
                             (<= property-definition (count %)))
                       property-name
                       (str "shorter than " property-definition))
    :max-length (check #(and (check-by-length? %)
                             (>= property-definition (count %)))
                       property-name
                       (str "longer than " property-definition))
    :type       (validate-type property-name property-definition)
    ;;:vector    (partial validate-vector property-name property-definition)
    :properties #(validate-schema all-optional? {:properties property-definition} %)
    ;;:children   (fn [v] (map #(#'validate-schema property-definition %) v))
    :children   (partial validate-vector all-optional? property-name property-definition)
    ;;:map        property-definition
    :in         (check #(contains? property-definition %)
                       property-name
                       (str "not in " property-definition))
    :regex      (check #(re-matches property-definition %)
                       property-name
                       (str "does not match " property-definition))))

;; todo: this is UGLY! this should not be done here...
;; updated 2021-09-27: better handling of null values during update
(defn- escape-optional? [property property-value all-optional?]
  (let [[property-name property-definition] property]
    (or (and all-optional?
             (not (contains? property-value property-name)))
        (and (or (:optional? property-definition)
                 (some? (:default property-definition)))
             (nil? (property-name property-value)))
        (:computed property-definition)
        (:derived property-definition))))

(def validation-keys [:min
                      :max
                      :min-length
                      :max-length
                      :type
                      :properties
                      :children
                      :in
                      :regex])

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

(defn validate-schema [all-optional? schema m]
  (let [{:keys [properties]} schema
        only-these-keys      (apply only-these (keys properties))
        rule                 (->> properties
                                  (map #(partial validate-property % all-optional?))
                                  (cons only-these-keys)
                                  (apply all-of))]
    (rule m)))

(deftest test-validate-vector
  (is (= [true {:field {}}]
         (validate-vector false :field :string ["123"])))
  (is (= [true {:field {}}]
         (validate-vector false :field :number ["123"])))
  (is (= [true {:field {}}]
         (validate-vector false
                          :field
                          {:properties {:field {:type :string}}}
                          [{:field "123"}])))
  (is (= [false {:field {:field1 "not a number"}}]
         (validate-vector false
                          :field
                          {:properties {:field1 {:type :number}}}
                          [{:field1 "123"}])))
  (is (= [false {:field {:field1 "not an integer"}}]
         (validate-vector false
                          :field
                          {:properties {:field1 {:type :integer}}}
                          [{:field1 123.45}])))
  (is (= [false {:field {:field1 "required property has no value"
                         :field2 "invalid attribute"}}]
         (validate-vector false
                          :field
                          {:properties {:field1 {:type :string}}}
                          [{:field2 "123"}])))
  (is (= [false {:field {:vector "not sequential"}}]
         (validate-vector false :field :string "123"))))

(deftest test-get-check-fn
  (with-redefs [check (spy/stub)]
    (get-check-fn false :min :field 1)
    (is (spy/call-matching? check (fn [l]
                                    (is (fn? (first l)))
                                    (is ((first l) 2))
                                    (is (= :field (second l)))
                                    (is (= "smaller than 1" (last l)))))))
  (with-redefs [check (spy/stub)]
    (get-check-fn false :max :field 1)
    (is (spy/call-matching? check (fn [l]
                                    (is (fn? (first l)))
                                    (is ((first l) 0))
                                    (is (= :field (second l)))
                                    (is (= "bigger than 1" (last l)))))))
  (with-redefs [validate-type (spy/stub)]
    (get-check-fn false :type :field :string)
    (is (spy/called-with? validate-type :field :string)))
  (with-redefs [validate-schema (spy/stub)]
    (let [res (get-check-fn false :properties :field :string)]
      (is (fn? res))
      (res "123")
      (is (spy/called-with? validate-schema false {:properties :string} "123"))))
  (with-redefs [validate-vector (spy/stub)]
    (let [res (get-check-fn false :children :field :string)]
      (is (fn? res))
      (res "123")
      (is (spy/called-with? validate-vector false :field :string "123"))))
  (with-redefs [check (spy/stub)]
    (get-check-fn false :in :field {:field :string})
    (is (spy/call-matching? check (fn [l]
                                    (is (fn? (first l)))
                                    (is ((first l) :field))
                                    (is (= :field (second l)))
                                    (is (= "not in {:field :string}" (last l)))))))
  (is (thrown-with-msg? java.lang.IllegalArgumentException
                        #"No matching clause"
                        (= "" (get-check-fn false :else "" "")))))

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
  (testing "A not set field with default false should not be checked"
    (is (escape-optional? [:field {:default false}] {} false)))
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

(deftest test-validate-schema
  (is (= [true {}]
         (validate-schema false
                          {:properties {:field1 {:type :string}
                                        :field2 {:type :number :optional? true}}}
                          {:field1 "123"
                           :field2 nil})))
  (is (= [false {:field1 "required property has no value"}]
         (validate-schema false
                          {:properties {:field1 {:type :string}}}
                          {:field1 nil})))
  (is (= [false {:field1 "not a string"}]
         (validate-schema false
                          {:properties {:field1 {:type :string}}}
                          {:field1 123})))
  (is (= [true {}]
         (validate-schema true
                          {:properties {:field1 {:type :string}
                                        :field2 {:type :number}}}
                          {:field1 "123"})))
  (is (= [true {}]
         (validate-schema false
                          {:properties {:values {:type :vector
                                                 :children {:type :string}}}}
                          {:values ["123"]})))
  (is (= [false {:values {:vector "not sequential"}}]
         (validate-schema false
                          {:properties {:values {:type :vector
                                                 :children {:type :string}}}}
                          {:values "123"})))
  (is (= [true {}]
         (validate-schema false
                          {:properties {:values {:type :map
                                                 :properties {:field2 {:type :string}}}}}
                          {:values {:field2 "123"}})))
  (let [result (validate-schema false
                                {:properties {:values {:type :map
                                                       :properties {:field2 {:type :string}}}}}
                                {:values "123"})]
    (is (= false (first result)))
    (is (= "not a map" (get-in result [1 :values]))))
  (is (= [true {}]
         (validate-schema false
                          {:properties {:values {:type :vector
                                                 :children {:type :string}}}}
                          {:values [123]})))
  (is (= [false {:field "smaller than 1"}]
         (validate-schema false
                          {:properties {:field {:type :number
                                                :min 1}}}
                          {:field 0})))
  (is (= [false {:field "bigger than 1"}]
         (validate-schema false
                          {:properties {:field {:type :number
                                                :max 1}}}
                          {:field 2})))
  (is (= [false {:field "shorter than 1"}]
         (validate-schema false
                          {:properties {:field {:type :string
                                                :min-length 1}}}
                          {:field ""})))
  (is (= [false {:field "longer than 1"}]
         (validate-schema false
                          {:properties {:field {:type :string
                                                :max-length 1}}}
                          {:field "ab"})))
  (is (= [false {:field "shorter than 1"}]
         (validate-schema false
                          {:properties {:field {:type :vector
                                                :children {:type :number}
                                                :min-length 1}}}
                          {:field []})))
  (is (= [false {:field "longer than 1"}]
         (validate-schema false
                          {:properties {:field {:type :vector
                                                :children {:type :number}
                                                :max-length 1}}}
                          {:field [1 2]})))
  (is (= [false {:field "does not match [0-9]+"}]
         (validate-schema false
                          {:properties {:field {:type :string
                                                :regex #"[0-9]+"}}}
                          {:field "abc"})))
  (is (= [true {}]
         (validate-schema false
                          {:properties {:field {:type :string
                                                :regex #"[0-9]+"}}}
                          {:field "0123"}))))
