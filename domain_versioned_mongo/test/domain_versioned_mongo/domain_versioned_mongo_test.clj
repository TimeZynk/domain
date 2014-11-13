(ns domain-versioned-mongo.domain-versioned-mongo-test
  (:require [midje.sweet                                                 :refer :all]
            [somnium.congomongo                                          :as mongo]
            [com.timezynk.domain.domain-versioned-mongo.mongo            :as vm]
            [com.timezynk.domain.domain-versioned-mongo.mongo-collection :as mc]
            [com.timezynk.domain.relation                                :as rel]
            [com.timezynk.domain.domain-versioned-mongo.channel          :as mchan]))

(def persons (mc/mongo-collection :persons))

(fact "mongo-collection is a function that creates a MongoCollection"
      persons => (mc/map->MongoCollection {:cname :persons :restriction {}}))

(facts "Add a new document/documents with conj!"

       (fact "You can add one document. _name will be set, and valid-from will be set."
             (rel/conj! persons {:name "Bertil"})
             => #(= (deref %)
                    [{:name       "Bertil"
                      :id         "<identity>"
                      :vid        "<vid>"
                      :valid-from "<timestamp>"}])
             (provided
              (mongo/insert! :persons
                             (checker [[{:keys [name _name valid-from]}]]
                                      (and name _name valid-from))
                             :many true)
              => [{:name       "Bertil"
                   :_name      "<identity>"
                   :_id        "<vid>"
                   :valid-from "<timestamp>"}]))

       (fact "You can also add a sequence of documents."
             (rel/conj! persons [{:name "Angus"}
                                 {:name "Sixten"}])
             => #(= (deref %)
                    [{:name       "Angus"
                      :id         "<identity-a>"
                      :vid        "<vid-a>"
                      :valid-from "<timestamp>"}
                     {:name       "Sixten"
                      :id         "<identity-s"
                      :vid        "<vid-s>"
                      :valid-from "<timestamp>"}])
             (provided
              (mongo/insert! :persons
                             (checker [[angus sixten]]
                                      (and (:name angus)
                                           (:_name angus)
                                           (:valid-from angus)
                                           (:name sixten)
                                           (:_name sixten)
                                           (:valid-from sixten)))
                             :many true)
              => [{:name       "Angus"
                   :_name      "<identity-a>"
                   :_id        "<vid-a>"
                   :valid-from "<timestamp>"}
                  {:name       "Sixten"
                   :_name      "<identity-s"
                   :_id        "<vid-s>"
                   :valid-from "<timestamp>"}])))

(facts "Fetch documents with a direct deref, or via select"

       (fact "Deref a collection to fetch all documents"
             @persons => [{:name "Bernhard", :vid "<vid>", :id "<id>", :valid-from "<timestamp>"}
                          {:name "Oswald", :vid "<vid>", :id "<id>", :valid-from "<timestamp>"}]
             (provided
              (mongo/fetch :persons
                           :where {:valid-to nil})
              => [{:name "Bernhard", :_id "<vid>", :_name "<id>", :valid-from "<timestamp>"}
                  {:name "Oswald", :_id "<vid>", :_name "<id>", :valid-from "<timestamp>"}]))

       (fact "Use select with a restriction to fetch a subset of all documents"
             @(rel/select persons {:name "Boris"}) => anything
             (provided
              (mongo/fetch :persons
                           :where {:valid-to nil, :name "Boris"})
              => []))

       (fact "Use select with valid-to set, to fetch previous versions"
             @(rel/select persons {:valid-to {:$gt "<timestamp>"}}) => anything
             (provided
              (mongo/fetch :persons
                           :where {:valid-to {:$gt "<timestamp>"}})
              => [])))

(facts "Update documents with update-in!"

       (fact "A new doc will be created.
              :valid-to will be set on the old version that still remains.
              :_name will be the same for old and new document.
              Updates via $set."
             (rel/update-in! persons
                             {:name "Gusten"}
                             {:nickname "Grodslukare"})
             => anything
             (provided
              (vm/now*) => "<ts>"
              (mongo/fetch :persons
                           :where {:valid-to nil
                                   :name     "Gusten"})
              => [{:name       "Gusten"
                   :_id        "<vid1>"
                   :_name      "<id1>"
                   :valid-from "<ts>"}
                  {:name       "Gusten"
                   :_id        "<vid2>"
                   :_name      "<id2>"
                   :valid-from "<ts>"}]
              (mongo/insert! :persons
                             [{:name       "Gusten"
                               :nickname   "Grodslukare"
                               :_name      "<id1>"
                               :_pid       "<vid1>"
                               :valid-from "<ts>"
                               :valid-to   nil}
                              {:name       "Gusten"
                               :nickname   "Grodslukare"
                               :_name      "<id2>"
                               :_pid       "<vid2>"
                               :valid-from "<ts>"
                               :valid-to   nil}]
                             :many true)
              => []
              (vm/update-oldie!* anything :persons
                                 {:_id      {:$in ["<vid1>" "<vid2>"]}
                                  :valid-to nil}
                                 {:$set {:valid-to "<ts>"}})
              => []))

       (fact "Use updaters to update a value with a function value"
             (rel/update-in! persons
                             {}
                             {:name (fn [s] (str s " Smith"))})
             => anything
             (provided
              (mongo/fetch :persons :where {:valid-to nil})
              => [{:name "Mr."}
                  {:name "Mrs."}]
              (mongo/insert! :persons
                             (checker [[mr mrs]]
                                      (and (= (:name mr) "Mr. Smith")
                                           (= (:name mrs) "Mrs. Smith")))
                             :many true)
              => []
              (vm/update-oldie!* anything :persons
                                 anything
                                 anything)
              => [])))

(fact "Destroy documents with disj!"
      (rel/disj! persons {:name "Albert"}) => anything
      (provided
       (vm/now*) => "<ts>"
       (mongo/update! :persons
                      {:name "Albert", :valid-to nil}
                      {:$set {:valid-to "<ts>"}}
                      :multiple true
                      :upsert false)
       => :unimportant))

(comment
  fact "When a document is created, updated or deleted, an event will be put to the mongo channel"
      "This will not be part of 0.2.0 though")
