(ns com.timezynk.domain.mongo.core
  "A persistence layer aimed at MongoDB with versioned collection"
  (:refer-clojure :exclude [compile conj! disj!])
  (:require
   [clojure.core.reducers :as r]
   [clojure.set :refer [rename-keys]]
   [clojure.walk :refer [postwalk-replace]]
   [com.timezynk.domain.mongo.channel :as mchan]
   [com.timezynk.useful.mongo :as um]
   [com.timezynk.useful.mongo.db :refer [db]]
   [com.timezynk.useful.rest.current-user :as current-session]
   [somnium.congomongo :as mongo]
   com.timezynk.domain.mongo.predicates)
  (:import [org.bson.types ObjectId]))


                                        ; handy


(def ^:const DISTINCT_LIMIT 60000)

(def ^:dynamic *debug* false)

(def ids-in {:id :_name, :vid :_id, :pid :_pid, "vid" :_id})
(def ids-out {:_name :id, :_id :vid, :_pid :pid})

(def predicate-symbols
  '{=    com.timezynk.domain.mongo.predicates/=*
    !=   com.timezynk.domain.mongo.predicates/!=*
    not= com.timezynk.domain.mongo.predicates/!=*
    <    com.timezynk.domain.mongo.predicates/<*
    >    com.timezynk.domain.mongo.predicates/>*
    <=   com.timezynk.domain.mongo.predicates/<=*
    >=   com.timezynk.domain.mongo.predicates/>=*
    and  com.timezynk.domain.mongo.predicates/and*
    or   com.timezynk.domain.mongo.predicates/or*
    not  com.timezynk.domain.mongo.predicates/not*
    exists com.timezynk.domain.mongo.predicates/exists
    ;like com.timezynk.domain.mongo.predicates/like
    ;nil? com.timezynk.domain.mongo.predicates/nil?*
    in   com.timezynk.domain.mongo.predicates/in})

(defn where* [clause]
  (->> clause (postwalk-replace predicate-symbols)))

(defmacro where [clause]
  (where* clause))

                                        ; mongo

;; todo, this might not be enough for restrictions. What if :id is not a "root" key?
(defn rename-ids-in [m]
  (rename-keys (dissoc m :_id :_name :_pid) ids-in))

(defn rename-ids-out [m]
  (rename-keys (dissoc m :id :vid :pid) ids-out))

(defn- set-default-values [m]
  (merge m
         (when-not (:_name m) {:_name (ObjectId.)})
         {:valid-from (System/currentTimeMillis)
          :valid-to   nil}))

(defn- insert! [cname new]
  (mongo/with-mongo @db
    (let [new (->> new
                   (r/map rename-ids-in)
                   (r/map set-default-values)
                   (into []))
          new (if (seq new) (mongo/insert! cname new :many true) [])
          new (map rename-ids-out new)]
      ; (debug (.getName (Thread/currentThread)) "insert! completed for" cname)
      (mchan/put! :insert cname new)
      ; (debug (.getName (Thread/currentThread)) "insert! mchan/put! completed for" cname)
      new)))

(defn- get-old-docs [cname restriction]
  (let [oldies (mongo/fetch cname :where restriction)]
    (if (sequential? oldies) oldies [oldies])))

(defn update-fast! [cname restriction new-doc]
  (mongo/with-mongo @db
    (let [now         (System/currentTimeMillis)
          restriction (postwalk-replace ids-in restriction)
          oldies      (get-old-docs cname (assoc restriction :valid-to nil))
          ids         (map :_id oldies)
          new-doc     (dissoc (rename-ids-in new-doc) :_id :_pid :_name)
          new-doc     (assoc new-doc :valid-from now :valid-to nil)
          _           (mongo/update! cname
                                     {:_id {:$in ids}}
                                     {:$set new-doc}
                                     :multiple true
                                     :upsert false)
          newlings    (map (fn [d] (merge d new-doc)) oldies)
          newlings    (map rename-ids-out newlings)
          oldies      (map rename-ids-out oldies)]
      (mchan/put! :update cname newlings oldies)
      newlings)))

(defn- update! [cname restriction new-doc]
  (mongo/with-mongo @db
    (let [now         (System/currentTimeMillis)
          restriction (postwalk-replace ids-in restriction)
          _           (mongo/update! cname
                                     (assoc restriction :valid-to nil)
                                     {:$set {:valid-to now}}
                                     :multiple true
                                     :upsert false)
          oldies      (get-old-docs cname (assoc restriction :valid-to now))
          company-id  (get (first oldies) :company-id)
          new-doc     (dissoc (rename-ids-in new-doc) :_id :_pid)
          create-new  (fn [old] (-> (dissoc old :_id)
                                    (merge new-doc)
                                    (assoc :valid-from now
                                           :valid-to nil
                                           :_pid (:_id old))))
          newlings    (map create-new oldies)
          newlings    (when (seq newlings)
                        (mongo/insert! cname
                                       newlings
                                       :many true))
          _           (when (seq oldies)
                        (mongo/insert! :domainlog
                                       {:type :update
                                        :company-id company-id
                                        :collection (name cname)
                                        :oldies oldies
                                        :tstamp now
                                        :user-id (or (current-session/user-id) (:changed-by new-doc))}))
          _           (mongo/destroy! cname (assoc restriction :valid-to now))
          newlings    (map rename-ids-out newlings)
          oldies      (map rename-ids-out oldies)]

      ; (debug (.getName (Thread/currentThread)) "update! completed for" cname)


      (mchan/put! :update cname newlings oldies)
      ; (debug (.getName (Thread/currentThread)) "update! mchan/put! completed for" cname)
      newlings)))

(defn destroy-fast! [cname restriction]
  (mongo/with-mongo @db
    (let [restriction (postwalk-replace ids-in restriction)
          oldies      (get-old-docs cname (assoc restriction :valid-to nil))
          ids         (map :_id oldies)
          result      (mongo/destroy! cname {:_id {:$in ids}})
          deleted-by  (current-session/user-id)
          oldies      (map rename-ids-out
                           (map
                            (fn [o] (assoc o :deleted-by deleted-by))
                            oldies))]
      (mchan/put! :delete cname nil oldies)
      {:deleted-no (.getN result)})))

(defn- destroy! [cname restriction]
  (mongo/with-mongo @db
    (let [now         (System/currentTimeMillis)
          query       (->> restriction
                           (postwalk-replace ids-in)
                           (merge {:valid-to nil}))
          deleted-by  (current-session/user-id)
          result      (mongo/update! cname
                                     query
                                     {:$set {:valid-to now :deleted-by deleted-by}}
                                     :multiple true
                                     :upsert false)
          deleted     (mongo/fetch cname
                                   :where (assoc query :valid-to now))
          company-id  (get (first deleted) :company-id)
          _           (when (seq deleted)
                        (mongo/insert! :domainlog
                                       {:type :delete
                                        :company-id company-id
                                        :collection (name cname)
                                        :oldies deleted
                                        :tstamp now
                                        :user-id deleted-by}))
          _           (mongo/destroy! cname (assoc query :valid-to now))]
      ; (debug (.getName (Thread/currentThread)) "destroy! completed for" cname)
      (mchan/put! :delete cname nil (map rename-ids-out deleted))
      ; (debug (.getName (Thread/currentThread)) "destroy! mchan/put! completed for" cname)
      {:deleted-no (.getN result)})))

(defn- fetch [cname restriction & options]
  (mongo/with-mongo @db
    (->>
     (apply mongo/fetch
            cname
            :where (->> restriction
                        (postwalk-replace ids-in)
                        (merge (when-not (:vid restriction) {:valid-to nil})))
            :sort {:_id 1}
            options)
     (map rename-ids-out))))

(defn- fetch-count [cname restriction]
  (mongo/with-mongo @db
    (mongo/fetch-count cname
                       :where (->> restriction
                                   (postwalk-replace ids-in)
                                   (merge (when-not (:vid restriction) {:valid-to nil}))))))

(defn add-change-meta [log-info o]
  (assoc o :log-info log-info))

(defn add-change-metas [d]
  (map (partial add-change-meta (assoc (dissoc d :oldies :_id) :id (:_id d)))
       (:oldies d)))

(defn created-info [entry]
  {:type "create"
   :tstamp (.getTime (:id entry))
   :user-id (:created-by entry)})

(defn shift-version-info* [entries]
  (r/reduce
   (fn [{:keys [entries log-info]} entry]
     (let [log-info (or log-info (created-info entry))]
       {:entries (conj entries (assoc entry :log-info log-info))
        :log-info (:log-info entry)}))
   {:entries []} entries))

(defn shift-version-info [latest entries]
  (let [{:keys [entries log-info]} (shift-version-info* entries)]
    (if latest
      (conj entries
            (assoc latest :log-info (or log-info (created-info latest))))
      (if log-info
        (conj entries
              (assoc (last entries) :log-info log-info))
        entries))))

(defn same-change? [{log-a :log-info arch-a :archived} {log-b :log-info arch-b :archived}]
  (and
   (= (:user-id log-a) (:user-id log-b))
   (= (:type log-a) (:type log-b))
   (= arch-a arch-b)
   (:tstamp log-a)
   (:tstamp log-b)
   (> DISTINCT_LIMIT (Math/abs (- (:tstamp log-b) (:tstamp log-a))))))

(defn join-close-entries [entries]
  (if (seq entries)
    (loop [acc []
           pending (first entries)
           prev (first entries)
           entry (first (rest entries))
           entries (rest (rest entries))]
      (if entry
        (if (same-change? pending entry)
          (recur acc pending entry (first entries) (rest entries))
          (recur (conj acc prev) entry entry (first entries) (rest entries)))
        (conj acc prev)))
    entries))

(defn fetch-log
  ([cname params]
   (fetch-log cname params
              (rename-ids-out
               (mongo/fetch-one cname :where {:_name (:id params) :valid-to nil}))))
  ([cname {:keys [id company-id booked-users]} latest]
   (let [id (um/object-id id)
         completed (um/object-id company-id)]
     (->> (mongo/fetch :domainlog
                       :where (merge
                               {:collection (name cname)
                                :oldies._name id
                                :company-id company-id}
                               (when booked-users
                                 {:oldies.booked-users (um/object-id booked-users)}))
                       :sort {:tstamp 1}
                       :hint "oldies._name_1")
          (r/mapcat add-change-metas)
          (r/filter (fn [d] (and (= (:_name d) id) (= (:company-id d) company-id))))
          (r/map rename-ids-out)
          (shift-version-info latest)
          (join-close-entries)))))

                                        ; MongoCollection

(defprotocol Relation
  (select [this predicate])
  (select-count [this] [this predicate])
  (conj! [this records])
  (disj! [this predicate])
  (update-in! [this predicate record]))

"The MongoCollection is inspired by the Table record in ClojureQL.
 It might be possible to migrate to a relational database in the future via this relationship."

(defrecord MongoCollection [cname restriction query-result
                            update-fn! destroy-fn!]

  clojure.lang.IDeref
  (deref [this]
    (if query-result
      query-result
      (fetch cname restriction)))

  Relation
  (select [this predicate]
    (update-in this [:restriction] merge predicate))

  (select-count [this]
    (assoc this :query-result (fetch-count cname restriction)))

  (select-count [this predicate]
    (select-count (update-in this [:restriction] merge predicate)))

  (conj! [this docs]
    (let [created (insert! cname (if (map? docs) [docs] docs))]
      (assoc this :query-result created)))

  (disj! [this predicate]
    (let [restriction (merge restriction predicate)
          wresult     (destroy-fn! cname restriction)]
      (assoc this :query-result wresult
             :restriction  restriction)))

  (update-in! [this predicate new-doc]
    (let [restriction (merge restriction predicate)
          result      (update-fn! cname restriction new-doc)]
      (assoc this :query-result result
             :restriction  restriction))))

(defmethod print-method MongoCollection [mcol ^java.io.Writer w]
  (when *debug*
    (doseq [[k v] mcol]
      (.write w (format "%s\t\t\t\t%s\n" (str k) (str v)))))
  (.write w (str "mongo collection "
                 (:cname mcol)
                 " :where "
                 (or (:restriction mcol) {}))))

(prefer-method clojure.pprint/code-dispatch clojure.lang.IPersistentMap clojure.lang.IDeref)

(defn mongo-collection [cname skip-logging]
  (map->MongoCollection
   (merge
    {:cname cname
     :restriction {}}
    (if skip-logging
      {:update-fn! update-fast!
       :destroy-fn! destroy-fast!}
      {:update-fn! update!
       :destroy-fn! destroy!}))))
