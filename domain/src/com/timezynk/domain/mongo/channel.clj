(ns com.timezynk.domain.mongo.channel
  (:require
   [com.timezynk.domain.context :as context]
   [com.timezynk.useful.channel :as c]
   [com.timezynk.useful.mongo.db :refer [db]]
   [com.timezynk.useful.prometheus.core :as metrics]
   [somnium.congomongo :as mongo])
  (:import [org.bson.types ObjectId]))

(def ^:const WAIT_TIMEOUT 10000)

(defonce channel (atom nil))

(defonce handler-time (metrics/counter :channel_handler_time_seconds
                                       "A counter of the total user time used for a handler"
                                       :function))

(defn f-wrapper [f]
  (let [fn-name (str f)]
    (fn [topic cname context [new-doc old-doc]]
      (mongo/with-mongo @db
        (let [start-time (System/nanoTime)
              current-request (or context/*request* {:id context})]
          (binding [context/*request* current-request]
            (f topic cname new-doc old-doc))
          (metrics/inc-by! handler-time (/ (double (- (System/nanoTime) start-time)) 1000000000.0) fn-name))))))

(defn put! [topic cname & {:keys [new old context]}]
  (when (and topic cname (or (seq new) (seq old)))
    (binding [c/*debug* false]
      (->> (map vector (or new (repeat nil))
                (or old (repeat nil)))
           (c/publish! @channel
                       (or context (:id context/*request*) (ObjectId.))
                       topic
                       cname)
           (c/wait-for WAIT_TIMEOUT)))))

(defn- init-channel []
  (compare-and-set! channel nil (.getId (Thread/currentThread)))
  (when (= (.getId (Thread/currentThread)) @channel)
    (reset! channel (c/start-channel!))))

(defn subscribe
  "Add new request response subscriber to topic. Wait until response is sent before next message is sent.
   Topic can be an array of topic or just a single topic."
  ([topic f] (subscribe topic nil f))
  ([topic collection-name f]
   (init-channel)
   (c/subscribe-request-response topic collection-name (f-wrapper f))))

(defn subscribe-broadcast
  "Add new request subscriber to topic. Messages are sent with lower priority and the next message is sent immediately.
   Topic can be an array of topic or just a single topic."
  [topic collection-name f]
  (init-channel)
  (c/subscribe-broadcast topic collection-name (f-wrapper f)))

(defn unsubscribe-all []
  (c/unsubscribe-all))
