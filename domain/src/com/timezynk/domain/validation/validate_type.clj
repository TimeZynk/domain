(ns com.timezynk.domain.validation.validate-type
  (:require
   [clojure.test :refer [deftest is]]
   [com.timezynk.domain.validation.check :refer [check]]
   [spy.core :as spy])
  (:import [org.bson.types ObjectId]
           [org.joda.time LocalDateTime LocalDate LocalTime]))

(defn- object-id? [x]
  (isa? (class x) ObjectId))

(defn- time? [x]
  (isa? (class x) LocalTime))

(defn- date-time? [x]
  (isa? (class x) LocalDateTime))

(defn- date? [x]
  (isa? (class x) LocalDate))

(defn- timestamp? [x]
  (and (number? x) (<= 0 x)))

(defn validate-type [attr-name type-name]
  (case type-name
    :string    (check string?     attr-name "not a string")
    :number    (check number?     attr-name "not a number")
    :integer   (check integer?    attr-name "not an integer")
    :duration  (check number?     attr-name "not a duration in milliseconds")
    :vector    (check sequential? attr-name "not sequential")
    :map       (check map?        attr-name "not a map")
    :time      (check time?       attr-name "not a valid time declaration")
    :date-time (check date-time?  attr-name "not a valid date-time declaration")
    :date      (check date?       attr-name "not a valid date")
    :timestamp (check timestamp?  attr-name "not a valid timestamp")
    :object-id (check object-id?  attr-name "not a valid id")
    :boolean   (check boolean?    attr-name "not a boolean")
    :any       (fn [_] [true #{}])
    [false {attr-name {"unknown type" (name type-name)}}]))

(deftest test-object-id
  (is (object-id? (ObjectId.)))
  (is (not (object-id? (str (ObjectId.))))))

(deftest test-time
  (is (time? (LocalTime. "12.03")))
  (is (not (time? "12.03"))))

(deftest test-date-time
  (is (date-time? (LocalDateTime. "2020-10-01T12.03")))
  (is (not (date-time? (LocalTime. "12.03")))))

(deftest test-date
  (is (date? (LocalDate. "2020-10-01")))
  (is (not (date? (LocalDateTime. "2020-10-01")))))

(deftest test-timestamp
  (is (timestamp? 12345))
  (is (not (timestamp? -12345))))

(deftest test-validate-type
  (with-redefs [check (spy/spy check)]
    (is (= [true {}]
           ((validate-type :field :string) "")))
    (is (= [false {:field "not a string"}]
           ((validate-type :field :string) 1)))
    (is (= [true {}]
           ((validate-type :field :object-id) (ObjectId.))))
    (is (= [false {:field "not a valid id"}]
           ((validate-type :field :object-id) (str (ObjectId.)))))
    (is (= [true #{}]
           ((validate-type :field :any) "")))
    (is (= [false {:field {"unknown type" "not-a-type"}}]
           (validate-type :field :not-a-type)))
    (is (spy/called-n-times? check 4))))
