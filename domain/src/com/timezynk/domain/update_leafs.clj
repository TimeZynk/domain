(ns com.timezynk.domain.update-leafs
  (:require [clojure.core.reducers :as r]))


                                        ; Update leafs


(declare update-leafs*)

(defn- update-value [trail fun & args]
  (fn [m p]
    (update-in m [p]
               #(apply update-leafs* (conj trail p) % fun %2)
               args)))

(defn- update-hash-map [p fun m & args]
  (r/reduce (apply update-value p fun args)
            m
            (keys m)))

(defn- update-leafs* [p v fun & args]
  (cond
    (sequential? v) (map #(apply update-leafs* (conj p []) % fun args) v)
    (map? v)        (apply update-hash-map p fun v args)
    :else           (apply fun p v args)))

(defn update-leafs
  "Recursive update on leafs in sequences and maps"
  [x fun & args]
  (apply update-leafs* () x fun args))
