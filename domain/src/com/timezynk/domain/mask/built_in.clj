(ns com.timezynk.domain.mask.built-in
  "Useful functions for masking DTC properties."
  (:require [com.timezynk.useful.cancan :as ability]
            [com.timezynk.domain.core :as c]))

(defn unauthorized?
  "Truthy if doc is missing authorization to contain the incoming property.
   Truthy if not called within a cancan scope.
   Falsy, otherwise."
  [dtc doc action property-name]
  (when (ability/in-scope?)
    (let [action (keyword (format "%s-property-%s"
                                  (name action)
                                  (name property-name)))
          object (c/get-dtc-name dtc)]
      (not (ability/can? action object doc)))))
