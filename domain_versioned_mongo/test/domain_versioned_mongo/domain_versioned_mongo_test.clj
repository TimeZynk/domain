(ns domain-versioned-mongo.domain-versioned-mongo-test
  (:require [midje.sweet                                                 :refer :all]
            [somnium.congomongo                                          :as mongo]
            [com.timezynk.domain.domain-versioned-mongo.mongo            :as vm]
            [com.timezynk.domain.domain-versioned-mongo.mongo-collection :as mc]
            [com.timezynk.domain.relation                                :as rel]
            [com.timezynk.domain.domain-versioned-mongo.channel          :as mchan]))

(def foos (mc/mongo-collection :foos))

(fact "mongo-collection is a function that creates a MongoCollection"
      foos => (mc/map->MongoCollection {:cname :foos :restriction {}}))

(facts "Add a new document/documents with conj!"
       (let [input (atom [:no])]
         (fact "You can add one document. _name will be set, and valid-from will be set."
               (rel/conj! foos {:name "Bertil"})
               => #(= (deref %)
                      [{:name       "Bertil"
                        :id         "<identity>"
                        :vid        "<vid>"
                        :valid-from "<timestamp>"}])
               (provided
                (mongo/insert! :foos
                               (checker [[{:keys [name _name valid-from]}]]
                                        (and name _name valid-from))
                               :many true)
                => [{:name  "Bertil"
                     :_name "<identity>"
                     :_id   "<vid>"
                     :valid-from "<timestamp>"}]))

         (fact "You can also add a sequence of documents."

               )))

(fact "Deref a collection to fetch all documents"
      )

(fact "Use fetch with a restriction to fetch a subset of all documents"
      )

(facts "Update documents with update-in!
        An update will create a new document.
        The old version will still be there"

       (fact "valid-to will be set on the old version"
             )

       (fact "_name will be the same for all versions of a document"
             ))

(fact "The value of a field, can be the result of a function, given as a field value"
      )

(fact "Destroy documents with disj!"
      )

(fact "The functions of the protocol Relation, which MongoCollection implements,
       returns the MongoCollection itself. This way queries can be composed in a flexible way."
      )

(fact "When a document is created, updated or deleted, an event will be put to the mongo channel"
      )
