(ns com.timezynk.domain.mongo.channel.context
  (:require [clojure.string :as string]
            [com.timezynk.bus.context :refer [Context]]
            [com.timezynk.domain.context :refer [*request*]])
  (:import [org.bson.types ObjectId]))

(defrecord WebRequestContext [request]
  Context
  (pretty-print [_]
    (let [{:keys [user path-info request-method]} request
          user-id (:id user)
          company-id (:company-id user)]
      (cond-> ""
        request-method (str (string/upper-case (name request-method)))
        path-info (str " " path-info)
        user-id (str " U:" user-id)
        company-id (str " C:" company-id)))))

(defn from-request
  "Wraps an HTTP request into a Context object."
  ([request]
   (when request
     (->WebRequestContext request)))
  ([]
   (from-request *request*)))

(defrecord PlaceholderContext [id]
  Context
  (pretty-print [_]
    (str id)))

(defn placeholder
  "Creates a Context object seeded with a random ID. Useful for tracking mchan
   jobs spawned from a single source."
  []
  (->PlaceholderContext (ObjectId.)))
