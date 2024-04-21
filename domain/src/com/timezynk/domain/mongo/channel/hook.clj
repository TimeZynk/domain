(ns com.timezynk.domain.mongo.channel.hook
  (:require [com.timezynk.useful.channel.subscriber.hook :refer [Hook]]
            [com.timezynk.useful.prometheus.core :as metrics]
            [com.timezynk.domain.context :as context]))

(defonce handler-time (metrics/counter :channel_handler_time_seconds
                                       "A counter of the total user time used for a handler"
                                       :function))

(defn- ->str [hook]
  (str (:f hook)))

(defrecord PerformanceTrackingHook [f]
  Hook

  (call [this topic cname context message]
    (let [fn-name (->str this)
          [new-doc old-doc] message
          start-at (System/nanoTime)
          current-request (or context/*request* {:id context})
          _ (binding [context/*request* current-request]
              (f topic cname new-doc old-doc))
          end-at (System/nanoTime)]
      (metrics/inc-by! handler-time
                       (/ (double (- end-at start-at)) 1000000000.0)
                       fn-name)))

  (pretty-print [this]
    (->str this))

  (report [_]
    "" ; TODO provide a helpful list of statistics here
       ; - average
       ; - min / max
       ; - ...
    ))
