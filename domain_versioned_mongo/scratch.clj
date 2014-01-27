(require :reload-all '[com.timezynk.domain.domain :as dom]
         '[com.timezynk.domain.persistence :as p]
         '[com.timezynk.domain.relation :as rel]
         '[domain-versioned-mongo.mongo :as m])

(require :reload-all '[domain-versioned-mongo.mongo-collection :refer [mongo-collection]])

"
Basic (CRUD)
============

Create a collection named persons and deref it to fetch all documents inside.
"
(def persons (mongo-collection :persons))

@persons
" => () No documents are created yet

Add a document with rel/conj!
"
(-> persons
    (rel/conj! {:name "Bernt" :age 35}))
"
Or add several documents at once
"
(-> persons
    (rel/conj! [{:name "August" :age 56}
                {:name "Signe" :age 34}]))
"
Create a query with the rel/where macro.
Keywords are handled as fields.
"
(dom/where (= :name "August"))

(dom/where (and (< :age 50)
                (> :age 34)))
"
Update a document with rel/update-in!. You can add a function as
a value. It will be invoked with the current value as argument and
the return value will be set as the new value.
"
(-> persons
    (rel/update-in! (dom/where (= :name "August"))
                    {:city "Malmö"
                     :age  inc}))

"
You can fetch all documents in a mongo collection just by dereferencing it,
but to restrict the result, you need to use rel/select.
"
@(-> persons
     (rel/select (dom/where (and (>= :age 30)
                                 (< :age 40)))))

"
Finally, to remove documents use disj!
"
(-> persons
    (rel/disj! (dom/where (> :age 50))))
"
Why not remove every document in the collection?
"
(-> persons
    (rel/disj! nil))

@persons
;;=> ()
"
A bit dangerous? Maybe... but remember, no documents are actually deleted from the collection,
but instead valid-to is set to a timestamp. You could 'undo' the delete by setting valid-to to nil again.
"
