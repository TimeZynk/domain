(ns domain-core.domain
  "Write a description"
  (:require [domain-core.validation             :as v]
            [domain-core.schema                 :as s]
            [domain-core.pack                   :as pack]
            [domain-core.persistence            :as p]
            [slingshot.slingshot                :refer [try+ throw+]]
            [domain-assembly-line.assembly-line :as line]
            [domain-core.relation               :as rel]
            [domain-core.assembly-lines         :as lines]
            ;[robert.hooke                         :refer [add-hook]]
            ;[compojure.core                       :refer [routes GET POST PUT PATCH DELETE]]
            ;[slingshot.slingshot                  :refer [throw+]]
            ;[tzbackend.util.rest                  :as rest :refer [json-response]]
            ;[com.timezynk.useful.cancan           :as ability]
            ;[tzbackend.future.domain.update-leafs :refer [update-leafs, update-leafs-via-directive]]
            ;tzbackend.util.init
            ))

(defrecord DomainTypeFactory [natural-names description name version
                              collection collects properties validate-doc old-docs
                              update!-line insert!-line fetch-line destroy!-line]

  Persistence
  (p/select [this]
    (fetch-line this))

  (p/select [this predicate]
    (p/select this predicate nil))

  (p/select [this predicate collects]
    (let [this (-> this
                   (update-in [:collection :restriction] merge predicate)
                   (update-in [:collects] merge collects))]
      (-> (fetch-line this)
          (line/prepare nil))))

  (p/project [this fields]
    (rel/project collection fields))

  (p/conj! [this]
    (insert!-line this))

  (p/conj! [this records]
    (-> (p/conj! this)
        (line/prepare records)))

  ;; todo! We have to handle updates of the environment of an assembly line
  (p/disj! [this]
    (-> (destroy!-line this)
        (line/prepare nil)) ; todo: this one should not be needed.
    )

  (p/disj! [this predicate]
    (-> (destroy!-line (update-in this [:collection :restriction] merge predicate))
        (line/prepare nil)))

  ;; todo! We have to handle updates of the environment of an assembly line
  (p/update-in! [this]
    (update!-line this))

  (p/update-in! [this predicate]
    (update!-line (-> this
                      (update-in [:collection :restriction] merge predicate)
                      (assoc-in [:collection :old-docs]
                                (rel/select (:collection this)
                                          (merge (:restriction this)
                                                 predicate))))))

  (p/update-in! [this predicate record]
    (-> (p/update-in! this predicate)
        (line/prepare record))))

(comment "Deprecated? Place in a function named 'print-info', or something similar" defmethod print-method DomainTypeFactory [dtc ^java.io.Writer w]
  (let [{:keys [insert!-line
                update!-line
                destroy!-line
                fetch-line
                description
                natural-names]} dtc
        p     (fn [^String s] (.write w s))
        nl    (fn [] (.write w "\n"))
        pline (fn [name line]
                (nl)
                (p name)
                (p (str (:station-order line))))]
    (when description
      (p "DESCRIPTION ")
      (p description) (nl))
    (p "COLLECTION  ")
    (p (name (collection-name dtc))) (nl) (nl)
    (when natural-names
      (p "IN NATURAL LANGUAGE ") (nl)
      (doseq [[k s] natural-names]
        (p (str (name k) " \"" s "\"")) (nl))
      (nl))
    (p "PROPERTIES") (nl)
    (pprint (:properties dtc))
    (pline "insert!-line  " (insert!-line dtc))
    (pline "update!-line  " (update!-line dtc))
    (pline "fetch-line    " (fetch-line dtc))
    (pline "destroy!-line " (destroy!-line dtc))))

(defmethod print-method DomainTypeFactory [dtc ^java.io.Writer w]
  (let [p (fn [& args] (.write w (apply str args)))]
    (p "DomainTypeCollection[" (:name dtc) "]")))


                                        ; Constructor

(defn dom-type [options & [default-properties]]
  {:pre [(get options :properties)
         (get options :name)
         (get options :collection)]}

  (map->DomainTypeFactory
   (merge {:update!-line  lines/update!
           :insert!-line  lines/insert!
           :destroy!-line lines/destroy!
           :fetch-line    lines/fetch}
          (-> options
              (update-in [:properties]
                         merge
                         (or default-properties {}))))))


                                        ;Aliases

(defmacro where [clause]
  (rel/where* clause))

(def execute! line/execute!)

(def add-stations line/add-stations)

(defn find-by-id [dom-type-factory id]
  (first @(p/select dom-type-factory
                    (#'where
                     (= :id id)))))
