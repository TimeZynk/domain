(ns com.timezynk.domain.relation
  (:refer-clojure :exclude [conj! disj!]))

(defprotocol Relation
  (select [this predicate])
  (project [this fields])
  (conj! [this records])
  (disj! [this predicate])
  (update-in! [this predicate record]))

(defmulti where* (fn [query & {:keys [db]}]
                   (or db :default)))

(defmacro where [clause]
  (where* clause))