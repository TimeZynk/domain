(ns com.timezynk.domain.schema.convert-test
  (:require [clojure.test :refer [deftest is are]]
            [spy.core :refer [stub]]
            [com.timezynk.domain.core :as dom]
            [com.timezynk.domain.mongo.core :as m]
            [com.timezynk.domain.persistence :as p]
            [com.timezynk.domain.schema :as s]
            [com.timezynk.useful.mongo :as um])
  (:import [java.time LocalDate ZonedDateTime ZoneId]
           [org.bson.types ObjectId]))

(def ^:private dom-type-collection
  "Shorthand for reducing repetition."
  (partial dom/dom-type-collection :name :abc))

(defn- select
  "Shorthand for selecting `doc` as if it were persisted."
  [dtc doc]
  (with-redefs [m/fetch (stub [doc])]
    (p/->1 dtc p/select)))

(deftest integer-str->number
  (let [dtc (dom-type-collection :properties {:x (s/number)})
        out (select dtc {:x "1234567890"})
        x-type (type (:x out))]
    (is (not= String x-type))
    (is (= Integer x-type))
    (is (= 1234567890 (:x out)))))

(deftest long-str->number
  (let [dtc (dom-type-collection :properties {:x (s/number)})
        out (select dtc {:x "12345678901"})
        x-type (type (:x out))]
    (is (not= String x-type))
    (is (= Long x-type))
    (is (= 12345678901 (:x out)))))

(deftest bigint-str->number
  (let [dtc (dom-type-collection :properties {:x (s/number)})
        out (select dtc {:x "12345678901234567890"})
        x-type (type (:x out))]
    (is (not= String x-type))
    (is (= clojure.lang.BigInt x-type))
    (is (= 12345678901234567890 (:x out)))))

(deftest float-str->number
  (let [dtc (dom-type-collection :properties {:x (s/number)})
        out (select dtc {:x "1.234"})
        x-type (type (:x out))]
    (is (not= String x-type))
    (is (= Float x-type))
    (is (= (float 1.234) (:x out)))))

(deftest double-str->number
  (let [dtc (dom-type-collection :properties {:x (s/number)})
        out (select dtc {:x "1.23456789"})
        x-type (type (:x out))]
    (is (not= String x-type))
    (is (= Double x-type))
    (is (= 1.23456789 (:x out)))))

(deftest bigdecimal-str->number
  (let [dtc (dom-type-collection :properties {:x (s/number)})
        out (select dtc {:x "1.23456789012345678"})
        x-type (type (:x out))]
    (is (not= String x-type))
    (is (= BigDecimal x-type))
    (is (= 1.23456789012345678M (:x out)))))

(deftest not-numeric-str->number
  (let [dtc (dom-type-collection :properties {:x (s/number)})
        in {:x "qwerty"}
        out (select dtc in)]
    (is (= String (type (:x out))))
    (is (= (:x in) (:x out)))))

(deftest valid-date-str->date
  (let [dtc (dom-type-collection :properties {:x (s/date)})
        out (select dtc {:x "2023-06-22"})
        x-type (type (:x out))]
    (is (not= String x-type))
    (is (= LocalDate x-type))
    (is (= (LocalDate/of 2023 6 22) (:x out)))))

(deftest not-date-str->number
  (let [dtc (dom-type-collection :properties {:x (s/date)})
        in {:x "qwerty"}
        out (select dtc in)]
    (is (= String (type (:x out))))
    (is (= (:x in) (:x out)))))

(deftest valid-datetime-str->datetime
  (let [dtc (dom-type-collection :properties {:x (s/date-time)})
        out (select dtc {:x "2023-06-22T10:47:32+03:00[Europe/Tallinn]"})
        x-type (type (:x out))]
    (is (not= String x-type))
    (is (= ZonedDateTime x-type))
    (is (= (ZonedDateTime/of 2023 6 22 10 47 32 0 (ZoneId/of "Europe/Tallinn"))
           (:x out)))))

(deftest not-datetime-str->datetime
  (let [dtc (dom-type-collection :properties {:x (s/date-time)})
        in {:x "qwerty"}
        out (select dtc in)]
    (is (= String (type (:x out))))
    (is (= (:x in) (:x out)))))

(deftest valid-objectid-str->objectid
  (let [dtc (dom-type-collection :properties {:x (s/id)})
        in {:x "52dd44373004b346e641112d"}
        out (select dtc in)
        x-type (type (:x out))]
    (is (not= String x-type))
    (is (= ObjectId x-type))
    (is (= (ObjectId. (:x in)) (:x out)))))

(deftest valid-objectid-keyword->objectid
  (let [dtc (dom-type-collection :properties {:x (s/id)})
        in {:x :52dd44373004b346e641112d}
        out (select dtc in)
        x-type (type (:x out))]
    (is (not= clojure.lang.Keyword x-type))
    (is (= ObjectId x-type))
    (is (= (um/object-id (:x in)) (:x out)))))

(deftest not-objectid-str->objectid
  (let [dtc (dom-type-collection :properties {:x (s/id)})
        in {:x "qwerty"}
        out (select dtc in)]
    (is (= String (type (:x out))))
    (is (= (:x in) (:x out)))))

(deftest integer->boolean
  (let [dtc (dom-type-collection :properties {:x (s/boolean)})]
    (are [in out] (->> {:x in} (select dtc) :x (= out))
         123     true
         "false" true
         0       true
         nil     false
         false   false
         -1      true)))

(deftest valid-date-str-within-vector->date
  (let [dtc (dom-type-collection :properties {:x (s/vector (s/date))})
        out (select dtc {:x ["2023-06-22" "2023-06-23"]})
        x (:x out)
        [date-1 date-2] x]
    (is (not-any? (comp #{String} type) x))
    (is (every? (comp #{LocalDate} type) x))
    (is (= (LocalDate/of 2023 6 22) date-1))
    (is (= (LocalDate/of 2023 6 23) date-2))))

(deftest valid-date-str-within-map->date
  (let [dtc (dom-type-collection :properties {:x (s/map {:y (s/date)})})
        out (select dtc {:x {:y "2023-06-22"}})
        y (get-in out [:x :y])
        type-y (type y)]
    (is (not= String type-y))
    (is (= LocalDate type-y))
    (is (= (LocalDate/of 2023 6 22) y))))

(deftest valid-date-str-deeply-nested->date
  (let [dtc (dom-type-collection :properties {:x (s/map {:y (s/vector (s/map {:z (s/date)}))})})
        out (select dtc {:x {:y [{:z "2023-06-22"}
                                 {:z "2023-06-23"}]}})
        y (get-in out [:x :y])
        [y-1 y-2] y]
    (is (not-any? (comp #{String} type :z) y))
    (is (every? (comp #{LocalDate} type :z) y))
    (is (= (LocalDate/of 2023 6 22) (:z y-1)))
    (is (= (LocalDate/of 2023 6 23) (:z y-2)))))
