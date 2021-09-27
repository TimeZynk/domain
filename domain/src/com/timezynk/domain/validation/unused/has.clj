(ns com.timezynk.domain.validation.unused.has
  (:require
   [com.timezynk.domain.validation.operator.all-of :refer [all-of]]
   [validateur.validation :as v]))

;; Unused functions

(defn has [& properties]
  (fn [entry]
    (let [rule (->> properties
                    (map #(v/presence-of %))
                    (apply all-of))]
      (rule entry))))

(defn no-presence-of [attribute]
  (fn [entry]
    (let [[presence-of?] ((v/presence-of attribute) entry)]
      (if presence-of?
        [false {attribute #{"have to be blank"}}]
        [true {attribute #{}}]))))

(defn has-not [& attributes]
  (fn [entry]
    (let [rule (->> attributes
                    (map #(no-presence-of %))
                    (apply all-of))]
      (rule entry))))
