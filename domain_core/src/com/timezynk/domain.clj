(ns com.timezynk.domain
  "Write a description"
  (:refer-clojure :exclude [conj! disj!])
  (:require [com.timezynk.domain.validation        :as v]
            [com.timezynk.domain.schema            :as s]
            [com.timezynk.domain.pack              :as pack]
            [com.timezynk.domain.factory           :as factory]
            [com.timezynk.domain.standard-lines    :as standard]
            [com.timezynk.domain persistence relation assembly-line]
            [potemkin :as pk]))

; Constructor

(defn dom-type
  ([options] (dom-type options nil))
  ([options default-properties]
    {:pre [(get options :properties)
           (get options :name)
           (get options :collection-factory)]}

    (factory/map->DomainTypeFactory
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
  [com.timezynk.domain.relation where]
  [com.timezynk.domain.persistence conj! select project disj! update-in!])

(defn find-by-id [dom-type-factory id]
  (first @(select dom-type-factory (#'where (= :id id)))))
