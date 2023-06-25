(ns com.timezynk.domain.schema.mask
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
  (:require [com.timezynk.domain.schema.walk :as sw]))

(defn build-station
  "Builds a station which redacts from doc those properties, which:
    * have been marked for masking
    * pass the mask test"
  [action]
  (fn [dtc doc]
    (sw/update-properties doc
                          (:properties dtc)
                          (fn [subdoc k spec]
                            (let [f (:mask spec)
                                  redact? (and (fn? f)
                                               (f dtc subdoc action (name k)))]
                              (cond-> subdoc
                                redact? (dissoc k)))))))
