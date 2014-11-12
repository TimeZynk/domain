(ns com.timezynk.domain.domain-versioned-mongo.channel
  (:require [clojure.core.reducers     :as r]
            [somnium.congomongo        :as mongo]
;            [tzbackend.util.channel    :as c]
;            [tzbackend.util.db         :refer [db]]
            ))

;; ;; This functionality is disabled to start with, since there are dependencies
;; ;; on tzbackend. Also, it should be placed in com.timezynk.domain.
;; ;;
;; ;; There are simply to little time and to much to do for now.

;; (def channel (c/chan))

;; (defn- f-wrapper [topic collection-name f]
;;   (fn [[_ [cname new old]]]
;;     (mongo/with-mongo db
;;       (when (or (nil? collection-name)
;;                 (= collection-name cname))
;;         (if old
;;           (f cname new old)
;;           (f cname new))))))

;; (defn put! [topic cname new & [old]]
;;   (let [f (fn [cname new old]
;;             (c/put! channel [topic [cname new old]]))]
;;     (dorun (map (partial f cname)
;;                 new
;;                 (or old (repeat nil))))))

;; (defn- pub-fn [[topic _]] topic)

;; (def publication (c/create-publication channel pub-fn))

;; (defn subscribe
;;   ([topic f] (subscribe topic nil f))
;;   ([topic collection-name f]
;;      (c/subscribe publication topic (f-wrapper topic collection-name f))))
