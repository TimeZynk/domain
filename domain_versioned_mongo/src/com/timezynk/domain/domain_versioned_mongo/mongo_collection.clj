(ns com.timezynk.domain.domain-versioned-mongo.mongo-collection
  (:refer-clojure :exclude [conj! disj!])
  (:require [com.timezynk.domain.relation :as rel]
            [com.timezynk.domain.domain-versioned-mongo.mongo :as m]))

(def ^:dynamic *debug* false)

"The MongoCollection is inspired by the Table record in ClojureQL.
 It might be possible to migrate to a relational database in the future via this relationship."

(defrecord MongoCollection [cname persistence-protocol restriction projection-keys
                            log query-result old-docs]

  clojure.lang.IDeref
  (deref [this]
    (if query-result
      query-result
      (apply m/fetch cname
             restriction
             (when projection-keys [:only projection-keys]))))

  rel/Relation
  (select [this predicate]
    (update-in this [:restriction] merge predicate))

  (project [this fields]
    (update-in this
               [:projection-keys]
               concat
               (conj fields :_name)))

  (conj! [this docs]
    (let [created (m/insert! cname
                           (if (map? docs) [docs] docs))]
      (-> this
          (assoc :query-result created)
          (update-in [:log :conj!-result] conj created))))

  (disj! [this predicate]
    (let [restriction (merge restriction predicate)
          wresult     (m/destroy! cname restriction)]
      (-> this
          (assoc :query-result wresult
                 :restriction  restriction)
          (update-in [:log :disj!-result] conj wresult))))

  (update-in! [this predicate new-doc]
    (let [restriction (merge restriction predicate)
          result      (m/update! cname restriction new-doc)
          ]
      (-> this
          (assoc :query-result result
                 :restriction  restriction)
          (update-in [:log :update-in!-result] conj result)))))

(defmethod print-method MongoCollection [mcol ^java.io.Writer w]
  (when *debug*
    (doseq [[k v] mcol]
      (.write w (format "%s\t\t\t\t%s\n" (str k) (str v)))))
  (.write w (str "mongo collection "
                 (:cname mcol)
                 " :where "
                 (or (:restriction mcol) {}))))

(prefer-method clojure.pprint/code-dispatch clojure.lang.IPersistentMap clojure.lang.IDeref)

(defn mongo-collection [cname]
  (map->MongoCollection {:cname       cname
                         :restriction {}}))

(def collection-name
  "Creates a collection name with optional version number and where
   '-' has been replaced by '.'"
  (memoize
   (fn [{:keys [name version]
        :or   {version ""}}]
     (-> (str (-> name
                  clojure.core/name
                  (clojure.string/replace #"\-" (constantly ".")))
              version)
         keyword))))

(defn mongo-collection-factory
  "Use this function with when you create a domain type factory"
  [domain-type-factory]
  (mongo-collection (collection-name domain-type-factory)))
