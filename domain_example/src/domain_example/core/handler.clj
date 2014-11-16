(ns domain-example.core.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [clojure.pprint :refer [pprint]]))

(defroutes app-routes

  (GET "/" [] "Hello World")
  (route/not-found "Not Found"))

(defn wrap-spy [handler]
  (fn [request]
    (println "-------------------------------")
    (println "Incoming Request:")
    (pprint request)
    (let [response (handler request)]
      (println "Outgoing Response Map:")
      (pprint response)
      (println "-------------------------------")
      response)))

(def app
  (-> app-routes
      wrap-spy
      (wrap-defaults site-defaults)))
