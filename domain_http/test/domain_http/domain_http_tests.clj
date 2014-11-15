(ns domain-http.domain-http-tests
  (:require [midje.sweet :refer :all]
            [com.timezynk.domain.http :as http]
            [com.timezynk.domain :as dom]
            [com.timezynk.domain.schema :as s]))

(dom/defdomtype cars
  {:name       :cars
   :collection-factory (constantly nil)
   :properties {:brand (s/string)
                :model (s/string)}})

(facts "A Domain Type Factory can have its own REST API created with the rest-routes function"
       ((http/rest-routes cars)
        {:method :get
         :url    "/cars"})
       => :todo)
