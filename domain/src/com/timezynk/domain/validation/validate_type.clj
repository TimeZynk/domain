(ns com.timezynk.domain.validation.validate-type
  (:require
   [com.timezynk.domain.validation.check :refer [check]])
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
  (get {:string    (check string?     attr-name "not a string")
        :number    (check number?     attr-name "not a number")
        :duration  (check number?     attr-name "not a duration in milliseconds")
        :vector    (check sequential? attr-name "not sequential")
        :map       (check map?        attr-name "not a map")
        :time      (check time?       attr-name "not a valid time declaration")
        :date-time (check date-time?  attr-name "not a valid date-time declaration")
        :date      (check date?       attr-name "not a valid date")
        :timestamp (check timestamp?  attr-name "not a valid timestamp")
        :object-id (check object-id?  attr-name "not a valid id")
        :boolean   (check boolean?    attr-name "not a boolean")
        :any       (fn [_] [true #{}])}
       type-name
       [false {attr-name {"unknown type" (name type-name)}}]))
