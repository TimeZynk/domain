(ns domain-example.domain
  "This file glue together the different parts"
  (:require [clojure.tools.logging                   :refer [spy]]
            [com.timezynk.domain                     :as dom]
            [com.timezynk.domain.schema              :as s]
            [com.timezynk.domain.domain-versioned-mongo.mongo-collection
             :refer [mongo-collection collection-name]]
            [com.timezynk.domain.domain-versioned-mongo.prop-types :as p])
  (:import [com.timezynk.domain.persistence Persistence]))

(defn dom-type [& {:as options}]
  (dom/dom-type (assoc options
                  :collection-factory #(mongo-collection
                                        (collection-name %)))
                p/default-props))

(defmacro defdomtype [n & opts]
  `(def ~n (dom-type ~@opts)))
