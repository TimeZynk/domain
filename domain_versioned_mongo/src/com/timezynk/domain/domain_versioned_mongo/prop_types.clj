(ns com.timezynk.domain.domain-versioned-mongo.prop-types
  (:require [com.timezynk.domain.schema :refer [defproptype]])
  (:import [org.bson.types ObjectId]))

(defproptype mongo-id
  :validate #(isa? (class %) ObjectId)
  :invalid-msg "Not a valid mongo id"
  :?->pack #(or (instance? ObjectId %)
                (re-find #"^[\da-f]{24}$" %))
  :pack #(ObjectId. %))

(defn auto-mongo-id [& options]
  (apply mongo-id :default (fn [_] (ObjectId.)) options))

(def id mongo-id)
(def auto-id auto-mongo-id)
