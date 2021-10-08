(ns com.timezynk.domain.validation.unused.compare
  (:import [org.joda.time LocalDateTime LocalDate LocalTime]))

;; Unused functions

(defn <or= [a b]
  (<= (compare a b) 0))

(defn >or= [a b]
  (>= (compare a b) 0))

(defn >not= [a b]
  (> (compare a b) 0))

(defn <not= [a b]
  (< (compare a b) 0))

(defprotocol Compare
  (lt*  [x y] "x is less than y?")
  (lt=* [x y] "x is less than or equals y")
  (eq*  [x y] "x equals y"))

(extend-protocol Compare
  LocalDateTime
  (lt*  [x y] (.isBefore x y))
  (lt=* [x y] (or (.isBefore x y) (= x y)))
  (eq*  [x y] (= x y))
  LocalDate
  (lt*  [x y] (.isBefore x y))
  (lt=* [x y] (or (.isBefore x y) (= x y)))
  (eq*  [x y] (= x y))
  LocalTime
  (lt*  [x y] (.isBefore x y))
  (lt=* [x y] (or (.isBefore x y) (= x y)))
  (eq*  [x y] (= x y)))

(defn- only-compare-existing-values [val-a val-b f]
  (if-not (and val-a val-b)
    [true {}]
    (f)))

(defn- compare-rule [compare-f message-f prop-a prop-b]
  (fn [m]
    (let [val-a (get m prop-a)
          val-b (get m prop-b)]
      (only-compare-existing-values val-a
                                    val-b
                                    #(if (compare-f val-a val-b)
                                       [true #{}]
                                       [false #{(message-f prop-a prop-b)}])))))

(def lt  (partial compare-rule lt*  #(str %1 " is not less than " %2)))
(def lt= (partial compare-rule lt=* #(str %1 " is not less than or equal to " %2)))
(def eq  (partial compare-rule eq*  #(str %1 " is not less equal to " %2)))
