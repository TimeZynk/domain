(ns com.timezynk.domain.mongo.channel.hook
  (:require [com.timezynk.bus.subscriber.hook :refer [Hook]]
            [com.timezynk.domain.context :as context]
            [com.timezynk.domus.mongo.db :refer [db]]
            [com.timezynk.useful.prometheus.core :as metrics]
            [somnium.congomongo :as mongo]))

(defonce handler-time (metrics/counter :channel_handler_time_seconds
                                       "A counter of the total user time used for a handler"
                                       :function))

(defn- ->str [hook]
  (str (:f hook)))

(defrecord PerformanceTrackingHook [f]
  Hook

  (id [this]
    (->str this))

  (call [this topic cname context message]
    (mongo/with-mongo @db
      (let [fn-name (->str this)
            [new-doc old-doc] message
            start-at (System/nanoTime)
            current-request (or context/*request* {:id context})
            _ (binding [context/*request* current-request]
                (f topic cname new-doc old-doc))
            end-at (System/nanoTime)]
        (metrics/inc-by! handler-time
                         (/ (double (- end-at start-at)) 1000000000.0)
                         fn-name))))

  (pretty-print [this]
    (->str this))

  (report [_]
    "" ; TODO provide a helpful list of statistics here
       ; - average
       ; - min / max
       ; - ...
    ))
