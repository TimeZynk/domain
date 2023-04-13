(ns com.timezynk.domain.mongo.channel
  (:require
   [com.timezynk.useful.channel :as c]
   [com.timezynk.domain.mongo.channel.context :as context]
   [com.timezynk.domain.mongo.channel.hook :refer [->PerformanceTrackingHook]]))

(def ^:const WAIT_TIMEOUT 10000)

(defonce channel (atom nil))

(defn put! [topic cname & {:keys [new old context]}]
  (when (and topic cname (or (seq new) (seq old)))
    (binding [c/*debug* false]
      (->> (map vector (or new (repeat nil))
                (or old (repeat nil)))
           (c/publish! @channel
                       (or context
                           (context/from-request)
                           (context/placeholder))
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
   (c/subscribe-request-response topic
                                 collection-name
                                 (->PerformanceTrackingHook f))))

(defn subscribe-broadcast
  "Add new request subscriber to topic. Messages are sent with lower priority and the next message is sent immediately.
   Topic can be an array of topic or just a single topic."
  [topic collection-name f]
  (init-channel)
  (c/subscribe-broadcast topic collection-name (->PerformanceTrackingHook f)))

(defn unsubscribe-all []
  (c/unsubscribe-all))
