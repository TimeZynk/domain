(ns com.timezynk.domain
  "Write a description"
  (:refer-clojure :exclude [conj! disj!])
  (:require [com.timezynk.domain.validation        :as v]
            [com.timezynk.domain.schema            :as s]
            [com.timezynk.domain.pack              :as pack]
            [com.timezynk.domain.factory           :refer [map->DomainTypeFactory]]
            [com.timezynk.domain.standard-lines    :as standard]
            [potemkin                              :as pk]
            [com.timezynk.domain persistence relation assembly-line]))

; Constructor

(defn dom-type
  ([options] (dom-type options nil))
  ([options default-properties]
    {:pre [(get options :properties)
           (get options :name)
           ;; What about another term for a factory, since
           ;; we already use that term inside of domain?
           (get options :collection-factory)]}
    (map->DomainTypeFactory
      (-> {:update!-line  standard/update!
           :insert!-line  standard/insert!
           :destroy!-line standard/destroy!
           :fetch-line    standard/fetch}
          (merge options)
          (update-in [:properties] merge default-properties)
          (assoc :collection ((:collection-factory options) options))))))

(defmacro defdomtype [n & opts]
  `(def ~n (dom-type ~@opts)))


;Aliases
(pk/import-vars
  [com.timezynk.domain.assembly-line execute! add-stations]
  [com.timezynk.domain.persistence conj! select project disj! update-in!]
  [com.timezynk.domain.pack pack-doc])

(defn by-vid [factory vid]
  (first @(select factory {:vid vid})))

(defn by-id [factory id]
  (first @(select factory {:id id})))
