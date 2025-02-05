(ns com.timezynk.domain.mongo.channel
  (:require
   [somnium.congomongo :as mongo]
   [com.timezynk.bus.core :as bus]
   [com.timezynk.bus.group :as cg]
   [com.timezynk.useful.env :as env]
   [com.timezynk.domain.mongo.channel.context :as context]
   [com.timezynk.domain.mongo.channel.hook :refer [->PerformanceTrackingHook]]))

(def ^:const WAIT_TIMEOUT 10000)

(def ^:const NUM_REQUEST_RESPONSE_WORKERS 2)

(def ^:const NUM_BROADCAST_WORKERS 2)

(def NUM_PERSISTED_WORKERS
  (env/parse-int-var "BACKGROUND_JOB_QUEUE_NUM_WORKERS" 2))

(def PERSISTED_THREAD_PRIORITY
  (env/parse-int-var "BACKGROUND_JOB_QUEUE_WORKER_PRIORITY"))

(def MIN_PERSISTED_INTERVAL
  (env/parse-int-var "BACKGROUND_JOB_QUEUE_MINIMUM_INTERVAL"))

(def MIN_PERSISTED_SLEEP
  (env/parse-int-var "BACKGROUND_JOB_QUEUE_MINIMUM_SLEEP"))

(defonce request-response (atom nil))

(defonce broadcast (atom nil))

(defonce persisted (atom nil))

(def ^:private ^:const NON_DOMAIN_FIELDS
  #{:id :vid :pid :lock-id
    :created-by :changed-by
    :created :valid-from :valid-to})

(defn- significant
  "Strips `doc` of extra-domain fields."
  [doc]
  (apply dissoc doc NON_DOMAIN_FIELDS))

(defn- changed?
  "True if the document pair represents domain-level change, false otherwise."
  [pair]
  (->> pair (map significant) (apply not=)))

(defn- pack
  "Packs documents into the message format which `bus` expects."
  [new-docs old-docs]
  (->> [new-docs old-docs]
       (map #(or % (repeat nil)))
       (apply map vector)))

(defn- debouncer
  "Builds a predicate which determines which message pair is not a NOOP."
  [topic]
  (some-fn (constantly (not= :update topic))
           changed?))

(defn put! [topic cname & {:keys [new old context]}]
  (when (and topic cname (or (seq new) (seq old)))
    (let [messages (->> (pack new old)
                        (filterv (debouncer topic)))
          context (or context
                      (context/from-request)
                      (context/placeholder))]
      (when (seq messages)
        (when @request-response
          (->> messages
               (bus/publish @request-response context topic cname)
               (bus/wait-for WAIT_TIMEOUT)))
        (when @broadcast
          (bus/publish @broadcast context topic cname messages))
        (when @persisted
          (bus/publish @persisted context topic cname messages))))))

(defn subscribe
  "Add new request response subscriber to topic. Wait until response is sent before next message is sent.
   Topic can be an array of topic or just a single topic."
  ([topic f] (subscribe topic nil f))
  ([topic collection-name f]
   (bus/subscribe @request-response
                  topic
                  collection-name
                  (->PerformanceTrackingHook f))))

(defn subscribe-broadcast
  "Add new request subscriber to topic. Messages are sent with lower priority and the next message is sent immediately.
   Topic can be an array of topic or just a single topic."
  [topic collection-name f]
  (bus/subscribe @broadcast
                 topic
                 collection-name
                 (->PerformanceTrackingHook f)))

(defn subscribe-persisted
  "Adds `f` as subscriber to `topic`. Persists messages in the database
   and manages them by means of a MongoDB queue.
   `topic` may a scalar value or a collection."
  [topic collection-name f]
  (bus/subscribe @persisted
                 topic
                 collection-name
                 (->PerformanceTrackingHook f)))

(defn unsubscribe-all []
  (bus/unsubscribe-all @request-response)
  (bus/unsubscribe-all @broadcast)
  (bus/unsubscribe-all @persisted))

(defn init [db]
  (mongo/with-mongo db
    (reset! request-response
            (-> (bus/create cg/REQUEST_RESPONSE)
                (bus/initialize NUM_REQUEST_RESPONSE_WORKERS)))
    (reset! broadcast
            (-> (bus/create cg/BROADCAST)
                (bus/initialize NUM_BROADCAST_WORKERS)))
    (reset! persisted
            (-> (bus/create cg/PERSISTED)
                (bus/initialize NUM_PERSISTED_WORKERS
                                {:queue-id :mchan_jobs
                                 :queue-collection :mchan.queue
                                 :thread-priority PERSISTED_THREAD_PRIORITY
                                 :min-interval MIN_PERSISTED_INTERVAL
                                 :min-sleep MIN_PERSISTED_SLEEP})))))

(defn destroy []
  (when @request-response
    (bus/destroy @request-response)
    (reset! request-response nil))
  (when @broadcast
    (bus/destroy @broadcast)
    (reset! broadcast nil))
  (when @persisted
    (bus/destroy @persisted)
    (reset! persisted nil)))
