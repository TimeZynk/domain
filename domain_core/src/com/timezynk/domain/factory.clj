(ns com.timezynk.domain.factory
  (:require [com.timezynk.domain.persistence      :as p]
            [com.timezynk.domain.assembly-line    :as line]
            [com.timezynk.domain.relation         :as rel]
            [clojure.pprint :refer [pprint]]))

(defrecord DomainTypeFactory [natural-names description name version restriction
                              collection collects properties validate-doc old-docs
                              update!-line insert!-line fetch-line destroy!-line]

  p/Persistence
  (p/select [this]
    (fetch-line this))

  (p/select [this predicate]
    (p/select this predicate nil))

  (p/select [this predicate collects]
    (let [this (-> this
                   (update-in [:restriction] merge predicate)
                   (update-in [:collects] merge collects))]
      (-> (fetch-line this)
          (line/prepare nil))))

  (p/project [this fields]
    (update-in this
               [:collection]
               rel/project
               fields)
    ;(rel/project collection fields)
    )

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
    (-> (destroy!-line (update-in this [:restriction] merge predicate))
        (line/prepare nil)))

  ;; todo! We have to handle updates of the environment of an assembly line
  (p/update-in! [this]
    (update!-line this))

  (p/update-in! [this predicate]
    (update!-line (-> this
                      (update-in [:restriction] merge predicate)
                      (assoc :old-docs (rel/select (:collection this) (merge restriction predicate))))))

  (p/update-in! [this predicate record]
    (-> (p/update-in! this predicate)
        (line/prepare record))))

(def collection-name
  "Creates a collection name with optional version number and where - has been replaced by ."
  (memoize
   (fn [dtc]
     (-> (str (-> (:name dtc)
                  name
                  (clojure.string/replace #"\-" (constantly ".")))
              (get dtc :version ""))
         keyword))))

(defmethod print-method DomainTypeFactory [dtc ^java.io.Writer w]
  (let [p (fn [& args] (.write w (apply str args)))]
    (p "DomainTypeCollection["
       (name (collection-name dtc))
       "]")))

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
