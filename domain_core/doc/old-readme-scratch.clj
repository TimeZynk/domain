(require '[clojure.tools.logging               :refer [spy info warn error]]
         '[slingshot.slingshot                 :refer [try+ throw+]]
         '[tzbackend.future.mongo              :as m]
         '[tzbackend.future.domain             :as dom]
         '[tzbackend.future.domain.recipe-core :as rc]
         '[tzbackend.future.domain.validation  :as v]
         '[tzbackend.future.domain.schema-core :as s]
         '[com.timezynk.useful.map             :as um]
         '[com.timezynk.useful.date            :as ud]
         '[com.timezynk.domain.assembly-line   :as line]
         '[tzbackend.future.mongo.channel      :as mchan]
         '[tzbackend.future.domain.pack        :as pack])
"
How to use the functionality in the future namespace
====================================================

future.mongo
------------
A DSL inspired by ClojureQL.
"


"
It's built around the type MongoCollection.
"
(def foos (m/mongo-collection :foos))

"
You add new document/documents with conj!, update them with update-in! and deletes
them with disj!.

*** conj!

You can add one ore a sequence of documents.
"
(m/conj! foos {:name "Bertil"})

(m/conj! foos [{:name "Gusten"}
               {:name "Astrid"}
               {:name "Hans" , :age 35}
               {:name "Gunnar", :age 46}])

"
*** update-in!

You restrict which documents to update with a restriction added as the secound argument.
The restriction is created via the where-macro (more about this later on).

You can update one ore many documents at a time.
"
(m/update-in! foos
              (m/where (= :name "Gusten"))
              {:nick-name "Grodslukare"})

"
*** disj!

Destroy a document via disj! (disjoin). You can destroy several documents at a time.
"
(m/disj! foos (m/where (or (= :name "Hans", :age 35)
                           (= :name "Gunnar"))))

"To view the collection, just deref it. If you want to restrict which document to fetch,
use select with a restriction."

@foos

@(m/select foos (m/where (:nick-name "Grodslukare")))

"
*** Predicates

Predicates is added via the where macro. This macro replaces the symbols of the expressions
inside of it. For example is 'and' replaced with 'tzbackend.future.mongo.predicates/and*'
and '=' is replaced with tzbackend.future.mongo.predicates/=*. As a result a mongo query will
be created (in the form of a hashmap congomongo can understand).

Keywords are interpreted as field names, so these can not be used as values. Use strings instead.
This makes it possible to distinguish field names from values without relying on the placement
of the arguments.

This macro could be used directly with congomongo if you want to.
"
(m/where (or (and (= :name "Hans")
                  (< 30 :age))
             (= "Gunnar" :name)))

"
*** Update functions

When you update a document you can add a function as a field value.
It will take the old value as the argument and return the new value.
"
(m/update-in! foos nil {:age #(if % (inc %) nil)}) ;; updates all document, since the restriction is nil

"
*** Composability

The functions of the protocol Relation, which MongoCollection implements, returns the MongoCollection itself.
This way queries can be composed in a flexible way.
"
(-> foos
    ;;(m/take 5)               ;take function not written yet
    ;;(m/sort [:age])          ;sort function not written yet
    (m/select (where (exists :age))))

"
*** The Channel

When documents are created, updated or deleted, this event will be put to a special core.async channel.
The topic will be either :update, :create or :delete and you can subscribe either of them. This is done
via the subscribe function in the tzbackend.future.mongo.channel namespace. The first argument is the topic
and the second one is the name of them mongo collection.
"


(mchan/subscribe :update :foos (fn [cname new-doc old-doc]
                                 (println "the document"
                                          new-doc
                                          "was updated in"
                                          cname
                                          "and its new value is"
                                          old-doc)))

(m/update-in! foos
              (m/where (= :name "Gunnar"))
              {:name #(str % " Gunnarsson")})


"
future.assembly-line
--------------------
The AssemblyLine replaces the ideas of recipes. The AssemblyLine was designed to
model CRUD operations, which typically consists of several steps that differs a
bit between different domain types, but still looks very similar for the most part.


*** Stations

The AssemblyLine consists of stations with one or several functions. They are defined
as a hashmap. Every entry is a station. The key is the name and the value
is the functions.

The functions have to parameters. The first is the environment, which is a value
used as a common ground through the process. The second one is the value >>in production<<.
This value is sent through the functions of the stations and the result of the last
function in the line will be the result of the whole line when derefed.

The stations is defined via a vector. Every other value is the name of a station
followed with its function or functions.

The environment – which is optional by the way – is added as a named parameter.
"

(def process-number (line/assembly-line [:process [*, +]
                                         :present (fn [env n]
                                                    (println "The number became" n)
                                                    n)]
                                        :environment 10))

process-number ;; take a look at the output of the assembly line, to see its stations and state

"
The stations can be replaced by new stations, or you can add new stations before or after
an existing station. This is done via the function add-stations, with the signature
add-stations [assembly-line placement target-station new-stations]. >>placement<< should
have the value :replace, :after or :before. >>target-station<< is the name of the station
refered to. >>new-stations<< is a vector defining the new stations in the same format as the
stations was defined in when the line was created.

This functionality makes the assembly lines very flexible and adaptable.
"

(def process-number-2
  (-> process-number
      (line/add-stations :before :process
                         [:validate (fn [env n]
                                      (if (< env n)
                                        (throw+ {:type :env<n
                                                 :message "Env should not be smaller than n"})
                                        n))])))

"
*** Wrapper Function

If there is need to, you can add a >>wrapper function<<. It is a function that wraps each
function in the line. With use of this, you can prepare the value in production for each step.
For example you can check if the value is a sequence or not and either call the function directly
with the in production value as an argument, or you could call it via map.

The wrapper function is added as a named parameter when the assembly line is created.
"

(defn wrapper-f [f env in-prod]
  (if (sequential? in-prod)
    (map (partial f env) in-prod)
    (f env in-prod)))

;; This assembly line can handle both numbers and sequences of numbers
(def process-number-3 (line/assembly-line [:validate
                                           (fn [env n]
                                             (if (< env n)
                                               (throw+ {:type :env<n
                                                        :env env
                                                        :message "Env should not be smaller than n"})
                                               n))
                                           :process
                                           [*, +]
                                           :present
                                           (fn [env n]
                                             (info "The number became" n)
                                             n)]
                                          :environment 10
                                          :wrapper-f wrapper-f))

"
*** Prepare, Execute and Deref

So far we have not executed the Assembly Lines. Before we do that we have to prepare
the assembly line. This is done via the prepare function. It takes a single argument.
This is the initial in production value. Then we can execute the assembly line and
process the in production value. The most simple way to do this is to deref the
assembly line. It will then execute from the start to the end and finally produce a
result value.
"
@(line/prepare process-number-4 9)

"
There is also a possibility to execute an assembly line explicitly via the execute!
function. It takes a station name as an optional argument. If a station name is given
the assembly line will walk through all stations up to the named station and halt.
If you later on deref or call execute on the paused assembly line, it will continue
from where it was.
"

(def process-number-4-validateed
  (-> process-number-4
      (line/prepare 9)
      (line/execute! :process)))

"
process-number-4-validated is now defined as an assembly-line, paused and ready to
continue with the :process station.
"
(def process-number-4-processed
  (-> process-number-4-validateed
      (line/execute! :present)))

"
Now the assembly line is paused and ready to run the last station, :present.
From here on, you could call execute! with no argument to finish the process, or
just deref it if you want to get the final value. Another possiblity is to add additional
stations if you want to further process the in production value.
"
@process-number-4-processed

"
*** Async

The assembly lines are async in nature."

(require '[clojure.core.async :as async :refer [go <! <!! >! timeout]])

(let [line-a (-> process-number-4
                 (line/add-stations :before :process [:delay (fn [_ n]
                                                          (<!! (timeout 500))
                                                          n)])
                 (line/prepare 5)
                 (line/execute!))
      line-b (-> process-number-4
                 (line/add-stations :before :process [:delay (fn [_ n]
                                                          (<!! (timeout 250))
                                                          n)])
                 (line/prepare 9)
                 (line/execute!))]
  (info "The lines are executed but not finished yet.")
  (spy (+ @line-a @line-b)))


"
future.domain
-------------

The tzbackend.future.domain.core namespace is built around the DomainTypeCollection.
It could have been named DomainTypeFactory, which actually might be a better name.

The DomainTypeCollection implements the Persistence protocol. It contains assembly lines
for all crud operations. These are quite complex and handles validation from different
perspectives (like type checks, contract like functionality and mandatory fields),
default values and so on.

These assembly lines uses future.mongo to communicate with the database. Every
DomainTypeCollection is tied to a MongoCollection via the :collection field.
"

"
*** Defining the DomainTypeCollection

DomainTypeCollections are created via the dom-type-collection function
– the DomainTypeCollection constructor. This will probably be done via
a macro in the future.
"
(def bars
  (dom/dom-type-collection
   :collection-name :bars ; the name of the mongo collection
   :natural-names {:sv "bars", :en "bars"} ; is not used, but might be a good idea to add anyway...
   :description   "This is just an example domain type" ; also not used yet, but might also be a good idea. If nothing else it's good documentation
   :validate-doc  (v/lt= :start :end) ; add a validation rule via tzbackend.util.schema.validation. This rule should validate the document on a documentation level – not on property level.
   :properties {:start (s/date-time)
                :end   (s/date-time)
                :counter (s/number)} ; This is the same as :properties on the old schemas. Will be merged with tzbackend.util.schema.schema-core/default-propertues.

   ;; This is where you customize the assembly lines, if you need to. There is a line for
   ;; every crud operation: :insert!-line, :update!-line, :fetch-line and :destroy!-line
   :update!-line (fn new-update!-line [%]
                   (-> (dom/update! %)
                       (line/add-stations :before :validate
                                          [:pre-validate
                                           ;; The DomTypeCollection is added as the environment.
                                           ;; In case of the update!-line the in-production value
                                           ;; is the document containing the new values.
                                           (fn must-increment [bars-coll bar]
                                             ;; this strange domain type have a value that needs
                                             ;; to be bigger for each update
                                             (let [;; old-bars below are all the old docs that will
                                                   ;; be affected by the update.
                                                   old-bars @(get-in bars-coll [:collection :old-docs])]
                                               (spy old-bars)
                                               (doseq [b old-bars]
                                                 (if-not (< (:counter b) (:counter bar))
                                                   (throw+ {:type    :tzbackend.util.schema.validation/validation-error
                                                            :message ":counter needs to be bigger each update"}))))
                                             bar)])))))

"
*** Interact with the DomainTypeCollection

To view some useful info about a collection, just view it via its print-method.
"
bars

"Via the print-method you can view some basic info about the assembly lines, to look closer at them,
you can call the corresponding functions with only the DomainTypeCollection as an argument.
This is a convenient way to explore the lines, but it's probably wiser to use these functions with
a full set of arguments otherwise, because they are not fully tested yet."

(dom/conj! bars) ;; insert!-line

(dom/update-in! bars) ;; update!-line

(dom/disj! bars) ;; delete!-line

(dom/select bars) ;; fetch-line

"Use conj! to add a new document.

The line is already prepared with the new document. Just execute!"
(-> bars
    (dom/conj! {:start (ud/->local-datetime "2014-01-01T10:00")
                :end   (ud/->local-datetime "2015-01-01T10:00")})
    dom/execute!)

"The previous example will fail, because it does not validate. Exception handling will have to improve.
It's a bit silent right now.

Another try"
(-> bars
    (dom/conj! {:start   (ud/->local-datetime "2014-01-01T10:00")
                :end     (ud/->local-datetime "2015-01-01T10:00")
                :counter 0})
    dom/execute!)

"You can add several documents at once."
(-> bars
    (dom/conj! [{:start   (ud/->local-datetime "2013-10-10T10:00")
                 :end     (ud/->local-datetime "2013-11-11T11:11")
                 :counter 2}
                {:start   (ud/->local-datetime "2011-11-11T11:11")
                 :end     (ud/->local-datetime "2012-11-11T11:11")
                 :counter 1}])
    (line/execute!))

"To fetch documents use select."
@(dom/select bars)

@(dom/select bars (dom/where (= :counter 1)))

"To update documents, use update-in! Note, you can update several documents at once!"
@(dom/update-in! bars
                 (m/where (< :counter 2))
                 {:counter 2})

"
To delete the documents, use disj!

You can delete several documents at once. This is a bit dangerous, but luckily a delete
actually just sets :valid-to to now.
"
(-> (dom/disj! bars) (line/execute! :deref)) ;; You just deleted all bars... maybe a bit dangerous after all

"Add a restriction to delete only some documents"
(-> bars
    (dom/disj! (m/where (= :counter 2)))
    (line/execute! :deref))


"
*** Pack input

To pack the doc, use the pack-doc function
"
(pack/pack-doc bars {:start   "2013-01-01T11:11"
                     :end     "2013-01-01T17:00"
                     :counter 2})
"
Normally, you want to do it as an integrated part of your interaction with the dom-type-collection.

You can add a station to the conj! or update-in! lines conveniently with pack-station. This station
also add a function which validate the input values and assure it is possible to pack them."

(-> bars
    (dom/conj! {})
    (dom/pack-station))

"
There is also a pack-query function. Unfortunately it's current form is a bit inflexible, because
it assumes one of the arguments will be a request map, and therefore a bit cumbersome to use outside
of the next part: the REST layer.

Pack query is not very competetent, but could be replaced by a much more intelligent function, for
example it would be nice to be able to create queries via the browser (via URLs).
"

"
*** REST

To autogenerate rest routes use the rest-routes function.

To generate an index, get, post, put and delete route just write the code below.
"
(defroutes http-routes
  (dom/rest-routes bars))

"
If you want to only generate routes for, for example, get and post write the code below.

Implicitly :get true and :post true is added.
"
(defroutes http-routes
  (dom/rest-routes bars
                   :index  false
                   :put    false
                   :delete false))

"
Every route generated by rest-routes executes and derefs an associated assembly line defined on the
DomainTypeCollection.

To alter the assembly line of a specific route,
pass a vector with station definitions as the argument of the named parameter.
"
(defroutes http-routes
  (dom/rest-routes bars
                   :put [:after :deref [:signal (fn [_ doc] :todo doc)]
                         :replace :validation []]))
