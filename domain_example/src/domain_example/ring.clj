(ns domain-example.ring
  (:use compojure.core
        [ring.middleware.format-params :only [wrap-json-params]]
        [ring.middleware.format-response :only [wrap-json-response]])
  (:require [clojure.tools.logging :as log :refer [info]]
            [compojure.handler     :as handler]
            [compojure.route       :as route]
            domain-example.person))

(defn init []
  (info "Init domain example"))

(defn init-dev []
  (info "Init domain example"))

(defn destroy []
  (info "Shutting down domain example"))

(defroutes api-routes
  ;;tzbackend.future.domain-types.journal-entry/http-routes
  domain-example.person/http-routes
  (route/not-found "Not found"))

(defn wrap-rest-middleware [handler]
  (-> handler
      ;(tzbackend.util.middleware/wrap-company-id)
      ;(ability/wrap-ability)
      ;(cancan/wrap-cancan)
      ;(tzbackend.session.middleware/wrap-session)
      ;(tzbackend.util.middleware/wrap-exceptions)
      (wrap-json-params)
      (wrap-json-response)
      ;(tzbackend.util.middleware/wrap-nocache)
      ;(tzbackend.util.locale/wrap-locale)
      (handler/api)))

(def app
  ;(db/wrap-mongo (wrap-rest-middleware api-routes) db/db)
  (wrap-rest-middleware api-routes))
