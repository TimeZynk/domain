(ns domain-example.person
  "An example domain type"
  (:require ;[domain-core.domain    :refer [rest-routes]]
            [domain-example.domain :refer [dom-type]]
            [domain-core.schema    :as s]))

(def persons
  (dom-type
   :name :persons
   :properties {:name (s/string)
                :age  (s/number :min 18)}))

;(def http-routes (rest-routes persons))
