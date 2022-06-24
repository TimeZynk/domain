(ns com.timezynk.domain.dtc.masks
  "Useful functions for masking DTC properties."
  (:require [com.timezynk.useful.cancan :refer [can? *ability*]]
            [com.timezynk.domain.core :as c]))

(defn unauthorized?
  "Truthy if doc is missing authorization to contain the incoming property.
   Truthy if the cancan/*ability* object is unbound.
   Falsy, otherwise."
  [dtc doc action property-name]
  (when (bound? #'*ability*)
    (let [action (keyword (format "%s-property-%s"
                                  (name action)
                                  (name property-name)))
          object (c/get-dtc-name dtc)]
      (not (can? action object doc)))))
