(ns com.timezynk.domain.domain-versioned-mongo.mongo
  "A persistence layer aimed at MongoDB with versioned collection"
  (:require ;[clojure.tools.logging          :as log :refer [spy info warn]]
            [clojure.core.reducers          :as r]
            [somnium.congomongo             :as mongo]
            [clojure.walk                   :refer [postwalk-replace postwalk]]
            ;[tzbackend.util.db              :refer [db]]
            [clojure.set                    :refer [rename-keys]]
            ;[clojure.core.async             :as a :refer [>! <! go pub sub]]

            ;; mchan disabled to start with.
            ;[domain-versioned-mongo.channel :as mchan]
            [com.timezynk.domain.relation           :as rel]
            ;domain-versioned-mongo.predicates
            )
  (:import [org.bson.types ObjectId]
           [org.joda.time LocalDateTime]))


;;todo This one can not be defined this way, of course!!
(def db (mongo/make-connection "domain"
                               :host "127.0.0.1"
                               :port 27017))

                                        ; handy

(def ^:dynamic *debug* false)

(def ids-in {:id :_name, :vid :_id, :pid :_pid, "vid" :_id})
(def ids-out {:_name :id, :_id :vid, :_pid :pid})

(defn now* []
  (System/currentTimeMillis))

(def predicate-symbols
  '{=    domain-versioned-mongo.predicates/=*
    !=   domain-versioned-mongo.predicates/!=*
    <    domain-versioned-mongo.predicates/<*
    >    domain-versioned-mongo.predicates/>*
    <=   domain-versioned-mongo.predicates/<=*
    >=   domain-versioned-mongo.predicates/>=*
    and  domain-versioned-mongo.predicates/and*
    or   domain-versioned-mongo.predicates/or*
    not  domain-versioned-mongo.predicates/not*
    exists domain-versioned-mongo.predicates/exists
    in   domain-versioned-mongo.predicates/in})


                                        ;Apply Updaters

(declare map-leaf-walk)

(defn- update-array [v-org v-upd fun]
  (if (map? v-upd)
    (map #(map-leaf-walk %1 %2 fun)
         v-org
         v-upd)
    (fun v-org v-upd)))

(defn- fill-with-nil [m m2]
  (r/reduce (fn [m key]
              (update-in m [key] identity))
            m
            (keys m2)))

(defn- map-leaf-walk [m-org m-upd fun]
  (into {} (for [[k-upd v-upd] m-upd
                 [k-org v-org] (fill-with-nil m-org m-upd)
                 :when (= k-upd k-org)
                 :let  [new-v (cond
                               (map? v-upd)        (map-leaf-walk v-org v-upd fun)
                               (sequential? v-upd) (update-array v-org v-upd fun)
                               :else               (fun v-org v-upd))]]
             [k-upd new-v])))

(defn- apply-updaters [old new]
  (map-leaf-walk old
                 new
                 (fn [old-v upd-v]
                   (if (fn? upd-v)
                     (upd-v old-v)
                     upd-v))))


                                        ; mongo

;; todo, this might not be enough for restrictions. What if :id is not a "root" key?
(defn- rename-ids-in [m]
  (rename-keys (dissoc m :_id :_name :_pid) ids-in))

(defn- rename-ids-out [m]
  (rename-keys (dissoc m :id :vid :pid) ids-out))

(defn insert! [cname new]
  (mongo/with-mongo db
    (let [new (->> new
                   (map rename-ids-in)
                   (map #(merge {:_name (ObjectId.)}
                                %
                                {:valid-from (now*)
                                 :valid-to   nil})))
          new (mongo/insert! cname new :many true)
          new (map rename-ids-out new)]
      ;(mchan/put! :insert cname new)
      new)))

(defn get-old-docs [cname restriction]
  (let [oldies (mongo/fetch cname :where (assoc restriction :valid-to nil))]
    (if (sequential? oldies) oldies [oldies])))

(defn update-oldie!* [db cname q v]
  (future ; was a go
    (mongo/with-mongo db
      (mongo/update! cname q v
                     :multiple true
                     :upsert false))))

(defn update! [cname restriction new-doc]
  (mongo/with-mongo db
    (let [restriction (postwalk-replace ids-in restriction)
          oldies      (get-old-docs cname restriction)
          old-ids     (map :_id oldies)
          now         (now*)
          new-doc     (dissoc (rename-ids-in new-doc) :_id :_pid)
          create-new  (fn [old] (-> (dissoc old :_id)
                                   (merge (apply-updaters old new-doc))
                                   (assoc :valid-from now
                                          :valid-to nil
                                          :_pid (:_id old))))
          newlings    (map create-new oldies)
          newlings    (mongo/insert! cname
                                     newlings
                                     :many true)
          _           (update-oldie!* db cname
                                      {:_id      {:$in old-ids}
                                       :valid-to nil}
                                      {:$set {:valid-to now}})
          newlings    (map rename-ids-out newlings)]
      ;(mchan/put! :update cname newlings oldies)
      newlings)))

(defn destroy! [cname restriction]
  (mongo/with-mongo db
    (let [now     (now*)
          query   (->> restriction
                       (postwalk-replace ids-in)
                       (merge {:valid-to nil}))
          result  (mongo/update! cname
                                 query
                                 {:$set {:valid-to now}}
                                 :multiple true
                                 :upsert false)
          deleted (mongo/fetch cname
                               :where (assoc query :valid-to now))]
      ;(mchan/put! :delete cname (spy deleted))
      result)))

(defn fetch [cname restriction & options]
  (mongo/with-mongo db
    (->>
     (apply mongo/fetch
            cname
            :where (->> restriction
                        (postwalk-replace ids-in)
                        (merge {:valid-to nil}))
            options)
     (map rename-ids-out))))
