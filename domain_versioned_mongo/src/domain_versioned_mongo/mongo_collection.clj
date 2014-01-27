(ns domain-versioned-mongo.mongo-collection
  (:refer-clojure :exclude [conj! disj!])
  (:require [com.timezynk.domain.relation         :as rel]
            [domain-versioned-mongo.mongo :as m]))

(def ^:dynamic *debug* false)

"The MongoCollection is inspired by the Table record in ClojureQL.
 It might be possible to migrate to a relational database in the future via this relationship."

(defrecord MongoCollection [cname persistence-protocol restriction projection-keys
                            log query-result]

  clojure.lang.IDeref
  (deref [this]
    (if query-result
      query-result
      (apply m/fetch cname
             restriction
             (when projection-keys [:only projection-keys]))))

  rel/Relation
  (rel/select [this predicate]
    (update-in this [:restriction] merge predicate))

  (rel/project [this fields]
    (update-in this [:projection-keys] concat fields))

  (rel/conj! [this docs]
    (let [created (m/insert! cname
                             (if (sequential? docs) docs [docs]))]
      (-> this
          (assoc :query-result created)
          (update-in [:log :conj!-result] conj created))))

  (rel/disj! [this predicate]
    (let [restriction (merge restriction predicate)
          wresult     (m/destroy! cname restriction)]
      (-> this
          (assoc :query-result wresult
                 :restriction  restriction)
          (update-in [:log :disj!-result] conj wresult))))

  (rel/update-in! [this predicate new-doc old-records]
    (let [restriction (merge restriction predicate)
          result      (m/update! cname restriction new-doc old-records)]
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
