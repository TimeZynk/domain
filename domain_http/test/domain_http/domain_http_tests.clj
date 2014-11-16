(ns domain-http.domain-http-tests
  (:require [midje.sweet                    :refer :all]
            [com.timezynk.domain.http       :as http]
            [com.timezynk.domain.date       :as date]
            [com.timezynk.domain            :as dom]
            [com.timezynk.domain.schema     :as s]
            [com.timezynk.domain.relation   :as rel]
            [com.timezynk.domain.middleware :refer [wrap-domain-query-and-collect-params]]))

(dom/defdomtype cars
  {:name       :cars
   :collection-factory (constantly :cars-collection)
   :properties {:id      (s/string :optional? true)
                :comment (s/string :optional? true)
                :brand   (s/string)
                :model   (s/string)}})

(defn spy-wrapper [handler]
  (fn [req]
    (println "req = " req)
    (handler req)))

;; Todo test
;;   errors: 400, validation-error
;;   collect functionality, or remove this functionality
;;   query parameters -> custom operators

(facts "A Domain Type Factory can have its own REST API created with the rest-routes function"
       (let [handler (-> (http/rest-routes cars)
                         spy-wrapper
                         wrap-domain-query-and-collect-params)
             car-doc {:id "1" :brand "Volvo", :model "745"}]

         (fact "Get all objects of a resource (GET /dom-type-name)"
               (handler
                {:request-method :get
                 :uri            "/cars"})
               => {:body [car-doc]
                   :headers {"Cache-Control" "no-cache"
                             "Content-Type"  "application/json"
                             "Expires"       "-1"}
                   :status 200}
               (provided
                (rel/select :cars-collection {})
                => (future [car-doc])))

         (fact "Getting a specific object (GET /dom-type-name/:id)"
               (handler
                {:request-method :get
                 :uri            "/cars/1"})
               => {:body    car-doc
                   :headers {"Cache-Control" "max-age=1,must-revalidate,private"
                             "Content-Type"  "application/json"
                             "Expires"       nil
                             "Last-Modified" "<rfc 123>"}
                   :status  200}
               (provided
                (date/to-rfc-1123 anything)
                => "<rfc 123>"
                (rel/select :cars-collection {:id "1"})
                => (future [car-doc])))

         (fact "Query params are handled as a restriction. They are automatically packed."
               (handler
                {:request-method :get
                 :uri            "/cars"
                 :query-params   {"brand" "Volvo" "q" "{\"model\":\"     850 \"}"}})
               => anything
               (provided
                (rel/select :cars-collection {:brand "Volvo", :model "850"})
                => (future [])))

         (fact "Create a new object (POST /dom-type-name)"
               (handler
                {:request-method :post
                 :uri            "/cars"
                 :body-params    car-doc})
               => {:body    car-doc
                   :headers {"Cache-Control" "no-cache"
                             "Content-Type"  "application/json"
                             "Expires"       "-1"}
                   :status  200}
               (provided
                (rel/conj! :cars-collection [car-doc])
                => (future [car-doc])))

         (fact "Update an object (PUT /dom-type-name/:id)"
               (handler
                {:request-method :put
                 :uri            "/cars/1"
                 :body-params    {:comment "Lorem ipsum."}})
               => {:body (assoc car-doc :comment "Lorem ipsum.")
                   :headers {"Cache-Control" "no-cache"
                             "Content-Type"  "application/json"
                             "Expires"       "-1"}
                   :status 200}
               (provided
                (rel/update-in! :cars-collection {:id "1"} {:id "1", :comment "Lorem ipsum."})
                => (future (assoc car-doc :comment "Lorem ipsum."))
                (rel/select :cars-collection {:id "1"}) ;; To be put in :old-docs
                => (future [car-doc])))

         (fact "Delete an object (DELETE /dom-type-name/:id)"
               (handler
                {:request-method :delete
                 :uri            "/cars/1"})
               => {:body    :whatever
                   :headers {"Cache-Control" "no-cache"
                             "Content-Type"  "application/json"
                             "Expires"       "-1"}
                   :status  200}
               (provided
                (rel/disj! :cars-collection {:id "1"})
                => (future :whatever)))))
