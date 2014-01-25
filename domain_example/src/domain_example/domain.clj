(ns domain-example.domain
  "This file glue together the different parts"
  (:require [clojure.tools.logging                   :refer [spy]]
            [domain-core.domain                      :as dom]
            [domain-core.schema                      :as s]
            [domain-versioned-mongo.mongo-collection :refer [mongo-collection
                                                             collection-name]])
  (:import [domain_core.persistence Persistence]))

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
                  :collection (mongo-collection
                               (collection-name options)))
                default-properties))
