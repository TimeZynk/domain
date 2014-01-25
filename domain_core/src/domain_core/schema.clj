(ns domain-core.schema
  (:refer-clojure :exclude [map vector string number boolean time] )
  (:require [clojure.core                         :as c]
            ;[clojure.tools.logging                :as log :refer [spy info warn]]
            [clojure.core.reducers                :as r]
            [domain-core.update-leafs :refer [update-leafs]]
            ;[tzbackend.session.current            :as current-session]
            )
  (:import [org.bson.types ObjectId]))

(defn- t [type]
  (fn [& {:as options}]
    (merge {:type type} options)))

(def id (t :object-id))

(defn auto-id [& options]
  (apply id :default #(ObjectId.) options))

(def string    (t :string))
(def number    (t :number))
(def boolean   (t :boolean))
(def date      (t :date))
(def date-time (t :date-time))
(def time      (t :time))
(def timestamp (t :timestamp))
(def any       (t :any))

(defn vector [children & options]
  (apply (t :vector) :children children options))

(defn map [properties & options]
  (apply (t :map) :properties properties options))

(comment defn render-schema "Prepares the schema to be serialized into JSON"
  [schema]
  (update-leafs schema replace-function))
