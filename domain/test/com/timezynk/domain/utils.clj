(ns com.timezynk.domain.utils
  (:require [spy.core :refer [stub]]
            [com.timezynk.domain.core :as dom]
            [com.timezynk.domain.mongo.core :as m]
            [com.timezynk.domain.persistence :as p]))

(defn build-immutable-inmemory-store
  "Builds a fixture which overrides mongo.core functions so that the end result
   is an immutable store. The store resides wholly in memory."
  [records]
  (fn [f]
    (with-redefs [m/fetch         (stub records)
                  m/insert!       (stub records)
                  m/update-fast!  (stub records)
                  m/update!       (stub records)
                  m/destroy-fast! (stub {})
                  m/destroy!      (stub {})]
      (f))))

(def dtc
  "Shorthand for reducing repetition."
  (partial dom/dom-type-collection :name :abc :properties))

(defn select
  "Shorthand for selecting `doc` as if it were persisted."
  [dtc doc]
  (with-redefs [m/fetch (stub [doc])]
    (p/->1 dtc p/select)))

(defn insert
  "Shorthand for inserting `doc` without persisting it."
  [dtc doc]
  (with-redefs [m/insert! (fn [_ doc] (into [] doc))]
    (p/->1 dtc (p/conj! doc))))
