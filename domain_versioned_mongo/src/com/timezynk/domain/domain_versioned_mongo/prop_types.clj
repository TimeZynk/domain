(ns com.timezynk.domain.domain-versioned-mongo.prop-types
  (:require [com.timezynk.domain.schema :refer [defproptype timestamp]])
  (:import [org.bson.types ObjectId]))

(defproptype mongo-id
  :validate #(isa? (class %) ObjectId)
  :invalid-msg "Not a valid mongo id"
  :str->pack #(or (instance? ObjectId %)
                (re-find #"^[\da-f]{24}$" %))
  :pack #(ObjectId. %))

(defn auto-mongo-id [& options]
  (apply mongo-id :default (fn [_] (ObjectId.)) options))

(def id mongo-id)
(def auto-id auto-mongo-id)

(def default-props {:id         (id :optional? true
                                    :remove-on-create? true)
                    :vid        (id :optional? true
                                    :remove-on-create? true)
                    :pid        (id :optional? true
                                    :remove-on-create? true)
                    :valid-to   (timestamp :optional? true
                                           :remove-on-create? true
                                           :remove-on-update? true)
                    :valid-from (timestamp :optional? true
                                           :remove-on-create? true
                                           :remove-on-update? true)})
