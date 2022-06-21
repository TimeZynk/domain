(ns com.timezynk.domain.dtc.masks
  "Useful functions for masking DTC properties."
  (:require [com.timezynk.useful.cancan :as ability]
            [com.timezynk.domain.core :as c]))

(defn authorization
  "True if doc is missing authorization to contain the incoming property."
  [dtc doc action property-name]
  (let [action (keyword (format "%s-property-%s"
                                (name action)
                                (name property-name)))
        object (c/get-dtc-name dtc)]
    (not (ability/can? action object doc))))
