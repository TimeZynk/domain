(ns com.timezynk.domain.json
  (:require [slingshot.slingshot :refer [throw+]]))

(declare not-found)

(defn json-response [body & {:keys [status headers] :or {status 200}}]
  (if body
    {:status status
     :headers
      (merge
        {"Content-Type" "application/json"
         "Expires" "-1"
         "Cache-Control" "no-cache"}
        headers)
     :body body}
    (not-found)))

(defn error [status message]
  (throw+ {:code status :error message}))

(defn not-found []
  (error 404 "Not found"))
