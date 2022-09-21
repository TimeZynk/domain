(ns com.timezynk.domain.mask
  (:require [clojure.string :as string]))

(defn- recurse?
  [property]
  (= :map (:type (val property))))

(defn- build-predicate
  "Builds a predicate which decides whether a (potentially nested) property
   should be removed from the document."
  [dtc doc action]
  (fn [trail]
    (let [mask (get-in dtc (concat (interleave (repeat :properties) trail)
                                   [:mask]))]
      (and (fn? mask)
           (mask dtc doc action (->> trail
                                     (map name)
                                     (string/join \.)))))))

(defn- mask*
  "Redacts all properties from doc for which redact? is true.
   Recurses for :map properties."
  [redact? dtc doc trail]
  (reduce (fn [acc property]
            (let [property-name (key property)
                  trail (conj trail property-name)]
              (cond-> acc
                (redact? trail) (dissoc property-name)
                (recurse? property)    (assoc property-name
                                              (mask* redact?
                                                     (val property)
                                                     (get acc property-name)
                                                     trail)))))
             doc
             (:properties dtc)))

(defn build-station
  "Builds a station which redacts from doc those properties, which:
    * have been marked for masking
    * pass the mask test"
  [action]
  (fn [dtc doc]
    (let [redact? (build-predicate dtc doc action)]
      (mask* redact? dtc doc []))))
