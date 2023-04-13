(ns com.timezynk.domain.migrations.utils
  (:require
   [clojure.tools.logging :as log]
   [com.timezynk.domain.migrations.collection-header :as h]
   ; [com.timezynk.domain.core :as dom]
   [somnium.congomongo :as mongo]))

(defn time-info [t-name company-name label f & args]
  (let [ts  (System/currentTimeMillis)
        res (apply f args)
        s   (int (/ (- (System/currentTimeMillis) ts) 1000))]
    (log/info t-name company-name ">>" label "took" s "seconds to complete <<")
    res))

                                        ; docompanies

(def company-counter
  (let [c (atom 0)]
    (fn []
      (swap! c inc))))

(defn- docompany [company f]
  (comment time-info (.getName (Thread/currentThread))
           (:name company)
           "migration"
           f
           company)
  (f company))

(defn- create-worker [companies f]
  (bound-fn []
    (loop [{:keys [company-id]} (.poll companies)]
      (when company-id
        (when-let [company (mongo/fetch-by-id :companies company-id :only [:name])]
          (try
            (log/info (.getName (Thread/currentThread)) "start migration for company #"
                      (company-counter) ":" (:name company) "(" (str (:_id company)) ") left to go:"
                      (.size companies))
            (docompany company f)
            (catch Exception e
              (log/error e (.getName (Thread/currentThread)) "failed to migrate company" company))))
        (recur (.poll companies))))
    (log/info (.getName (Thread/currentThread)) "finished")))

(defn- spawn-worker! [f companies]
  (let [^Thread t (Thread. (create-worker companies f))]
    (.start t)
    t))

(defn- spawn-workers! [companies n f]
  (doall (map (partial spawn-worker! f) (repeat n companies))))

(defn- build-companies-queue []
  (java.util.concurrent.ConcurrentLinkedQueue.
   (->> (mongo/fetch :companies :only [:_id])
        (map (fn [company] {:company-id (:_id company)})))))

(defn docompanies
  "do stuff per company"
  ([f] (docompanies f 4))
  ([f num-workers]
   (try
     (let [companies (build-companies-queue)
           workers   (spawn-workers! companies num-workers f)]
       (doseq [^Thread w workers]
         (log/info "Waiting for thread" (.getName w))
         (.join w)
         (log/info "Joined" (.getName w)))
       true)
     (catch Exception e (log/error e)))))

                                        ; migration

(defn continue? [migr-name collections]
  (every? true?
          (map #(not (h/migration-made? % migr-name)) collections)))

(defn finished! [migr-name collections]
  (doseq [c collections]
    (h/migration-finished c migr-name)))

(defn migration [migr-name & {:keys [task collections]}]
  (fn []
    (let [migr-name (keyword migr-name)]
      (try
        (when (continue? migr-name collections)
          (log/info "execute" migr-name)
          (if (task)
            (do (log/info "migration" migr-name "finished")
                (finished! migr-name collections))
            (log/info "migration" migr-name "did not finish")))
        (catch Exception e
          (log/error e "Migration failed"))))))

(defn migration-wrapper [migr]
  (fn [db]
    (mongo/with-mongo db
      (migr))))

                                        ; memory database

#_(def ^:dynamic *memory* nil)

#_(def ^:dynamic *now* nil)

#_(defn execute! [_ doc]
    (swap! *memory* conj
           (-> doc
               (rename-keys {:id :_name})
               (assoc :valid-from *now*))))

#_(defmacro batch [domain-type-collection & body]
    `(binding [*memory* (atom [])
               *now*    (System/currentTimeMillis)]
       ~@body
       (mongo/mass-insert! (dom/collection-name domain-type-collection)
                           @*memory*)))
