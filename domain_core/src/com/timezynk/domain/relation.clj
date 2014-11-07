(ns com.timezynk.domain.relation
  (:refer-clojure :exclude [conj! disj!]))

(defprotocol Relation
  (select [this predicate])
  (project [this fields])
  (conj! [this records])
  (disj! [this predicate])
  (update-in! [this predicate record]))
