(ns domain.domain-core-tests
  (:require [midje.sweet                        :refer :all]
            [midje.open-protocols               :refer :all]
            [com.timezynk.domain                :as dom]
            [com.timezynk.domain.validation     :as v]
            [com.timezynk.domain.schema         :as s]
            [com.timezynk.domain.assembly-line  :as line]
            [com.timezynk.domain.standard-lines :as standard]
            [com.timezynk.domain.relation       :as rel]
            [com.timezynk.domain.persistence    :as p]
            [clojure.pprint                     :refer [pprint]])
  (:import [org.joda.time LocalDateTime]))

"
com.timezynk.domain
-------------------

The tzbackend.future.domain.core namespace is built around the DomainTypeFactory.

The DomainTypeFactory implements the Persistence protocol. It contains assembly lines
for all crud operations. These are quite complex and handles validation from different
perspectives (like type checks, contract like functionality and mandatory fields),
default values and so on.

These assembly lines uses future.mongo to communicate with the database. Every
DomainTypeFactory is tied to a collection via the :collection-factory field.
These factories take the dom-type options as argument and produce a suitable
implementation of the Relation protocol.

*** Defining the DomainTypeFactory

DomainTypeFactories are created via the defdomtype macro – the DomainTypeFactory constructor.
"

(defrecord-openly MockCollection [name]

    rel/Relation
    (rel/conj! [this document])
    (rel/select [this predicate])
    (rel/update-in! [this predicate doc])
    (rel/disj! [this predicate])

    clojure.lang.IDeref
    (deref [this]
      "Just a fake collection"))

(defn mock-collection-factory [{:keys [name]}]
  (MockCollection. name))

(def foo {:foo (s/map {:foo2 (s/string)})
          :str (s/string)})

(dom/defdomtype bars
  {:name               :bars
   :collection-factory mock-collection-factory
   :validate-doc       (v/lt= :start :end)
   :properties         {:start    (s/date-time)
                        :end      (s/date-time)
                        :counter  (s/number)
                        :ts       (s/timestamp :optional? true)
                        :bool     (s/boolean   :optional? true)
                        :str      (s/string    :optional? true)
                        :str-vec  (s/vector (s/string) :optional? true)
                        :deep     (s/map foo :optional? true)
                        :deep-vec (s/maps foo :optional? true)}})

(def mock-coll (:collection bars))

(facts "Basic stuff about the factory"

       (fact "The factory collection implements com.timezynk.domain.relation/Relation"
             (satisfies? rel/Relation (mock-collection-factory {:name :abc}))
             => true)

       (fact "The factory is bound to the Var"
             bars => truthy)

       (fact "The factory implements com.timezynk.domain.persistence/Persistence"
             (satisfies? p/Persistence bars)
             => true))

(facts "You can get the assembly lines from the instantiated dom-type"

       (fact "Get conj! assembly line from factory"
             (satisfies? line/AssemblyLineExecute (dom/conj! bars))
             => true)

       (fact "Get update-in! assembly line from factory"
             (satisfies? line/AssemblyLineExecute (dom/update-in! bars))
             => true)

       (fact "Get disj! assembly line from factory"
             (satisfies? line/AssemblyLineExecute (dom/disj! bars))
             => true)

       (fact "Get select assembly line from factory"
             (satisfies? line/AssemblyLineExecute (dom/select bars))
             => true))

(facts "Execute the conj! assembly line to add new documents"

       (fact "Create a new document via the conj! line"
             (let [document  {:counter 2
                              :start   (LocalDateTime. 2012 1 1 10 0)
                              :end     (LocalDateTime. 2013 1 1 10 0)}]
               @(dom/conj! bars document) => document
               (provided
                (rel/conj! mock-coll anything) => (future [document]))))

       ;; Todo: The validation functionality should be tested much more
       (fact "Throw an exception if the input is invalid."
             (let [document {;; :counter 2 ;mandatory, but missing
                             :start (LocalDateTime. 2013 1 1 10 0)
                             :end   (LocalDateTime. 2014 1 1 10 0)}]
               @(dom/conj! bars document) => (throws Exception)))

       (fact "You can add several documents at once"
             (let [documents [{:start   (LocalDateTime. 2014 1 1 10 0)
                               :end     (LocalDateTime. 2014 1 1 11 0)
                               :counter 2}
                              {:start   (LocalDateTime. 2014 1 2 10 0)
                               :end     (LocalDateTime. 2014 1 2 11 0)
                               :counter 1}]]
               @(dom/conj! bars documents)
               => documents
               (provided
                (rel/conj! mock-coll anything) => (future documents)))))

(facts "Execute the select assembly line to fetch documents"

       (fact "Fetch all documents"
             (let [documents [{:start   (LocalDateTime. 2014 1 1 10 0)
                               :end     (LocalDateTime. 2014 1 1 11 0)
                               :counter 2}
                              {:start   (LocalDateTime. 2014 1 1 10 0)
                               :end     (LocalDateTime. 2014 1 1 11 0)
                               :counter 2}
                              {:start   (LocalDateTime. 2014 1 2 10 0)
                               :end     (LocalDateTime. 2014 1 2 11 0)
                               :counter 1}]]
               (count @(dom/select bars {})) => 3
               (provided
                (rel/select mock-coll {}) => (future documents))))

       (fact "Fetch restricted selection"
             (let [documents [{:start   (LocalDateTime. 2014 1 2 10 0)
                               :end     (LocalDateTime. 2014 1 2 11 0)
                               :counter 1}]]
               (count @(dom/select bars {:counter 1})) => 1
               (provided
                (rel/select mock-coll {:counter 1}) => (future documents)))))

(fact "Execute the update-in! assembly line to update documents"
      (let [updated-documents [{:start   (LocalDateTime. 2014 1 1 10 0)
                                :end     (LocalDateTime. 2014 1 1 11 0)
                                :counter 3}
                               {:start   (LocalDateTime. 2014 1 1 10 0)
                                :end     (LocalDateTime. 2014 1 1 11 0)
                                :counter 3}]]
        @(dom/update-in! bars {:counter 2} {:counter 3})
        => updated-documents
        (provided
         (rel/update-in! mock-coll {:counter 2} {:counter 3})
         => (future updated-documents))))

(facts "Execute the disj! assembly line to destroy documents"

       (fact "There exists a dangerous possibility to delete all documents at once"
             @(dom/disj! bars)
             => :all-docs-deleted
             (provided
              (rel/disj! mock-coll nil) => (future :all-docs-deleted)))

       (fact "Delete with restriction"
             @(dom/disj! bars {:counter 2})
             => :some-docs-deleted
             (provided
              (rel/disj! mock-coll {:counter 2}) => (future :some-docs-deleted))))

(facts "Use the pack functionality to prepare the input for the assembly lines"
       ;; Todo: remove joda dependency
       (fact "Input might be transformed and coerced"
             (dom/pack-doc bars {:start   "2013-01-01T11:11"
                                 :end     "2013-01-01T17:00"
                                 :counter "2"
                                 :ts      "1415804523291"
                                 :bool    "true"
                                 :str     " Lorem ipsum    "})
             => {:start   (LocalDateTime. 2013 1 1 11 11)
                 :end     (LocalDateTime. 2013 1 1 17 0)
                 :counter 2
                 :ts      (Long/parseLong "1415804523291")
                 :bool    true
                 :str     "Lorem ipsum"})

       (fact "Input might be transformed and coerced even in nested maps and vectors"
             (dom/pack-doc bars {:start   "2013-01-01T11:11"
                                 :end     "2013-01-01T17:00"
                                 :counter "2"
                                 :str-vec [" a" "b  "]
                                 :deep    {:foo {:foo2 "   c"}
                                           :str " d "}
                                 :deep-vec [{:foo {:foo2 "   c"}
                                             :str " d "}]})
             => {:start    (LocalDateTime. 2013 1 1 11 11)
                 :end      (LocalDateTime. 2013 1 1 17 0)
                 :counter  2
                 :str-vec  ["a" "b"]
                 :deep     {:foo {:foo2 "c"}
                            :str "d"}
                 :deep-vec [{:foo {:foo2 "c"}
                             :str "d"}]})

       ;; todo Test all pack-functionality around queries

       ;; todo Test nested pack-functionality
       )


;; Todo: Test HTTP layer. This might actually be an own project ...
;; ================================================================

;;  "
;; *** REST

;; To autogenerate rest routes use the rest-routes function.

;; To generate an index, get, post, put and delete route just write the code below.
;; "
;;  (defroutes http-routes
;;    (dom/rest-routes bars))

;;  "
;; If you want to only generate routes for, for example, get and post write the code below.

;; Implicitly :get true and :post true is added.
;; "
;;  (defroutes http-routes
;;    (dom/rest-routes bars
;;                     :index  false
;;                     :put    false
;;                     :delete false))

;;  "
;; Every route generated by rest-routes executes and derefs an associated assembly line defined on the
;; DomainTypeFactory.

;; To alter the assembly line of a specific route,
;; pass a vector with station definitions as the argument of the named parameter.
;; "
;;  (defroutes http-routes
;;    (dom/rest-routes bars
;;                     :put [:after :deref [:signal (fn [_ doc] :todo doc)]
;;                           :replace :validation []]))

;;  )
