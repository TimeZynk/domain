(ns com.timezynk.domain.mask
  "Implementation of DTC property masks.

   A property is redacted from the document if the following conditions are met:
    * property has been annotated with a :mask f attribute
    * f is a function
    * (f dtc doc action property-name) is truthy at the time of deferral

   Example:
   (defn mf
     \"Property :y is read-only, disable writing!\"
     [dtc doc action property-name]
     (not= :read action))

   (def dtc
     (dom-type-collection :name :qwerty
                          :properties {:x (s/string)
                                       :y (s/string :mask mf)
                                       :z (s/string)}))"
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
  (->> (:properties dtc)
       (filter (comp (set (keys doc)) key))
       (reduce (fn [acc property]
                 (let [property-name (key property)
                       trail (conj trail property-name)]
                   (cond-> acc
                     (redact? trail) (dissoc property-name)
                     (recurse? property) (assoc property-name
                                                (mask* redact?
                                                       (val property)
                                                       (get acc property-name)
                                                       trail)))))
               doc)))

(defn- any-masks?
  "Truthy if any property bears a :mask attribute, falsy otherwise.
   Recurses into :map properties."
  [dtc]
  (some (some-fn (comp fn? :mask val)
                 (every-pred recurse? (comp any-masks? val)))
        (:properties dtc)))

(defn build-station
  "Builds a station which redacts from doc those properties, which:
    * have been marked for masking
    * pass the mask test"
  [action]
  (fn [dtc doc]
    (if (any-masks? dtc)
      (mask* (build-predicate dtc doc action) dtc doc [])
      doc)))
