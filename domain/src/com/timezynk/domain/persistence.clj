(ns com.timezynk.domain.persistence
  (:refer-clojure :exclude [conj! disj!])
  (:require [com.timezynk.assembly-line :as line]
            [com.timezynk.useful.mongo :as um]))

(defprotocol Persistence
  (select [this] [this predicate] [this predicate collects])
  (select-count [this] [this predicate])
  (conj! [this] [this records])
  (disj! [this] [this predicate])
  (update-in! [this] [this predicate] [this predicate record]))

(defmacro ->1 [& steps]
  `(-> ~@steps
       deref
       first))

(defmacro ->! [& steps]
  `(-> ~@steps
       (line/execute! :deref)))

(def execute! line/execute!)

(defn by-vid [dom-type-collection vid]
  (when vid
    (first @(select dom-type-collection {:vid (um/object-id vid)}))))

(defn by-id [dom-type-collection id]
  (when id
    (first @(select dom-type-collection {:id (um/object-id id)}))))
