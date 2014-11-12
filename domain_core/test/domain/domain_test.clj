;; (ns domain.domain-test
;;   (:require [clojure.test                       :refer :all]
;;             [com.timezynk.domain                :as dom]
;;             [com.timezynk.domain.validation     :as v]
;;             [com.timezynk.domain.schema         :as s]
;;             [com.timezynk.domain.assembly-line  :as line]
;;             [com.timezynk.domain.standard-lines :as standard]
;;             [com.timezynk.domain.relation       :as rel]
;;             [com.timezynk.domain.persistence    :as p]
;;             [clojure.pprint                     :refer [pprint]])
;;   (:import [org.joda.time LocalDateTime]))

;; "
;; com.timezynk.domain
;; -------------------

;; The tzbackend.future.domain.core namespace is built around the DomainTypeFactory.

;; The DomainTypeFactory implements the Persistence protocol. It contains assembly lines
;; for all crud operations. These are quite complex and handles validation from different
;; perspectives (like type checks, contract like functionality and mandatory fields),
;; default values and so on.

;; These assembly lines uses future.mongo to communicate with the database. Every
;; DomainTypeFactory is tied to a collection via the :collection-factory field.
;; These factories take the dom-type options as argument and produce a suitable
;; implementation of the Relation protocol.

;; *** Defining the DomainTypeFactory

;; DomainTypeFactories are created via the defdomtype macro – the DomainTypeFactory constructor.
;; "

;; (def  ^:dynamic *DB*)

;; (defn add-docs [old neW]
;;   (concat old neW))

;; (defn create-mock-collection [restriction last-result]
;;   (reify
;;     rel/Relation
;;     (rel/conj! [this document]
;;       (swap! *DB* conj document)
;;       (create-mock-collection restriction document))

;;     (rel/select [this predicate]
;;       (create-mock-collection (merge restriction predicate) nil))

;;     (rel/update-in! [this predicate doc]
;;       (create-mock-collection (merge restriction predicate) doc))

;;     clojure.lang.IDeref
;;     (deref [this]
;;       (comment or last-result @*DB*)
;;       (comment pprint (filter
;;                        #(let [m (for [[pred-key pred-val] restriction
;;                                       [key val]           %]
;;                                   [key val])]
;;                           (= restriction (into {} m)))
;;                        @*DB*))
;;       (or last-result
;;           (filter
;;            #(let [m ;; Stupid query engine
;;                   (for [[pred-key pred-val] restriction
;;                         [key val]           %
;;                         :when               (and (= pred-key key) (= pred-val val))]
;;                     [key val])]
;;               (= restriction (into {} m)))
;;            @*DB*)))))

;; (defn mock-collection-factory [{:keys [name]}]
;;   (create-mock-collection nil nil))


;; (deftest collection-factory-must-produce-relations
;;   (is (satisfies? rel/Relation (mock-collection-factory {:name :abc}))))

;; (dom/defdomtype bars
;;   {:name               :bars ; the name of the mongo collection
;;    :collection-factory mock-collection-factory
;;    :validate-doc       (v/lt= :start :end) ; add a validation rule via tzbackend.util.schema.validation. This rule should validate the document on a documentation level – not on property level.
;;    :properties         {:start   (s/date-time)
;;                         :end     (s/date-time)
;;                         :counter (s/number)} ; This is the same as :properties on the old schemas. Will be merged with tzbackend.util.schema.schema-core/default-propertues.
;;    })

;; (deftest create-dom-type
;;   (is (not (nil? bars)))
;;   (is (satisfies? p/Persistence bars)))

;; "
;; You can get the assembly lines from the instantiated dom-type
;; "

;; (deftest get-assembly-lines
;;   (is (satisfies? line/AssemblyLineExecute (dom/conj! bars))) ;; insert!-line
;;   (is (satisfies? line/AssemblyLineExecute (dom/update-in! bars))) ;; update!-line
;;   (is (satisfies? line/AssemblyLineExecute (dom/disj! bars))) ;; delete!-line
;;   (is (satisfies? line/AssemblyLineExecute (dom/select bars)))) ;; fetch-line


;; "Execute the assembly lines to manipulate documents.

;; Use conj! to add a new document:"

;; (deftest add-document-to-collection
;;   (binding [*DB* (atom [])]
;;     (let [document {:counter 2
;;                     :start (LocalDateTime. 2012 1 1 10 0)
;;                     :end   (LocalDateTime. 2013 1 1 10 0)}]
;;       (is (= document @(dom/conj! bars document))))))

;; ;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; "The dom-type-factory will validate its input before trying to insert a new document
;; into the collection. If we remove the mandatory counter parameter it will therefore
;; throw an exception."

;; (deftest validation-error
;;   (binding [*DB* (atom [])]
;;     (let [document {:start (LocalDateTime. 2013 1 1 10 0)
;;                     :end   (LocalDateTime. 2014 1 1 10 0)}]
;;       (is (thrown? Exception @(dom/conj! bars document))))))



;; "You can add several documents at once."

;; (deftest add-several-at-once
;;   (binding [*DB* (atom [])]
;;     (let [documents [{:start   (LocalDateTime. 2014 1 1 10 0)
;;                       :end     (LocalDateTime. 2014 1 1 11 0)
;;                       :counter 2}
;;                      {:start   (LocalDateTime. 2014 1 2 10 0)
;;                       :end     (LocalDateTime. 2014 1 2 11 0)
;;                       :counter 1}]
;;           res       @(dom/conj! bars documents)]
;;       (is (= documents res)))))


;; "To fetch documents use select."

;; (deftest find-all-documents
;;   (binding [*DB* (atom [{:start   (LocalDateTime. 2014 1 1 10 0)
;;                          :end     (LocalDateTime. 2014 1 1 11 0)
;;                          :counter 2}
;;                         {:start   (LocalDateTime. 2014 1 1 10 0)
;;                          :end     (LocalDateTime. 2014 1 1 11 0)
;;                          :counter 2}
;;                         {:start   (LocalDateTime. 2014 1 2 10 0)
;;                          :end     (LocalDateTime. 2014 1 2 11 0)
;;                          :counter 1}])]
;;     (is (= 3
;;            (count @(dom/select bars {}))))))

;; (deftest find-restricted-selection
;;   (binding [*DB* (atom [{:start   (LocalDateTime. 2014 1 1 10 0)
;;                          :end     (LocalDateTime. 2014 1 1 11 0)
;;                          :counter 2}
;;                         {:start   (LocalDateTime. 2014 1 1 10 0)
;;                          :end     (LocalDateTime. 2014 1 1 11 0)
;;                          :counter 2}
;;                         {:start   (LocalDateTime. 2014 1 2 10 0)
;;                          :end     (LocalDateTime. 2014 1 2 11 0)
;;                          :counter 1}])]
;;     (is (= 1
;;            (count @(dom/select bars {:counter 1}))))))

;; (comment
;;   "
;; To delete the documents, use disj!

;; You can delete several documents at once. This is a bit dangerous, but luckily a delete
;; actually just sets :valid-to to now.
;; "
;;  (-> (dom/disj! bars) (line/execute! :deref)) ;; You just deleted all bars... maybe a bit dangerous after all

;;  "Add a restriction to delete only some documents"
;;  (-> bars
;;      (dom/disj! (m/where (= :counter 2)))
;;      (line/execute! :deref))


;;  "
;; *** Pack input

;; To pack the doc, use the pack-doc function
;; "
;;  (pack/pack-doc bars {:start   "2013-01-01T11:11"
;;                       :end     "2013-01-01T17:00"
;;                       :counter 2})
;;  "
;; Normally, you want to do it as an integrated part of your interaction with the dom-type-collection.

;; You can add a station to the conj! or update-in! lines conveniently with pack-station. This station
;; also add a function which validate the input values and assure it is possible to pack them."

;;  (-> bars
;;      (dom/conj! {})
;;      (dom/pack-station))

;;  "
;; There is also a pack-query function. Unfortunately it's current form is a bit inflexible, because
;; it assumes one of the arguments will be a request map, and therefore a bit cumbersome to use outside
;; of the next part: the REST layer.

;; Pack query is not very competetent, but could be replaced by a much more intelligent function, for
;; example it would be nice to be able to create queries via the browser (via URLs).
;; "

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
