(ns domain-example.domain
  "This file glue together the different parts"
  (:require [clojure.tools.logging                   :refer [spy]]
            [com.timezynk.domain                     :as dom]
            [com.timezynk.domain.schema              :as s]
            [com.timezynk.domain.domain-versioned-mongo.mongo-collection
             :refer [mongo-collection collection-name]])
  (:import [com.timezynk.domain.persistence Persistence]))

;; Todo: These should be part of domain core, right?
(def default-properties {:id         (s/id :optional? true
                                           :remove-on-create? true)
                         :vid        (s/id :optional? true
                                           :remove-on-create? true)
                         :pid        (s/id :optional? true
                                           :remove-on-create? true)
                         :valid-from (s/timestamp :optional? true
                                                  :remove-on-create? true
                                                  :remove-on-update? true)
                         :valid-to   (s/timestamp :optional? true
                                                  :remove-on-create? true
                                                  :remove-on-update? true)})

(defn dom-type [& {:as options}]
  (dom/dom-type (assoc options
                  :collection-factory #(mongo-collection
                                        (collection-name %)))
                default-properties))

(defmacro defdomtype [n & opts]
  `(def ~n (dom-type ~@opts)))
