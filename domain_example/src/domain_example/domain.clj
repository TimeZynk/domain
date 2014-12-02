(ns domain-example.domain
  (:require [com.timezynk.domain :as dom]
            [com.timezynk.domain.domain-versioned-mongo.mongo-collection :refer [mongo-collection-factory]]
            [com.timezynk.domain.domain-versioned-mongo.prop-types :as pt]))

(defn dom-type [options]
  (dom/dom-type (assoc options
                  :collection-factory mongo-collection)
                pt/default-props))

(defmacro defactory [n descr & {:as options}]
  (let [kword (keyword n)
        opts  (merge {:name kword :description descr} options)]
    `(def ~n ~descr (dom-type ~opts))))
