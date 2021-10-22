(ns com.timezynk.domain.validation.compare
  (:require
   [clojure.test :refer [deftest is testing]])
  (:import [org.joda.time LocalDateTime LocalDate LocalTime]))

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
                                       [true {}]
                                       [false #{(message-f prop-a prop-b)}])))))

(def lt  (partial compare-rule lt*  #(str %1 " is not less than " %2)))
(def lt= (partial compare-rule lt=* #(str %1 " is not less than or equal to " %2)))
(def eq  (partial compare-rule eq*  #(str %1 " is not equal to " %2)))

(deftest test-compare
  (let [start (LocalDate. "2010-01-01")
        end   (LocalDate. "2010-01-02")]
    (testing "lt"
      (is (= [true {}]
             ((lt :start :end) {:start start
                                :end end})))
      (is (= [false #{":start is not less than :end"}]
             ((lt :start :end) {:start end
                                :end end})))
      (is (= [true {}]
             ((lt :start :end) {:start start})))
      (is (= [true {}]
             ((lt :start :end) {:start nil
                                :end end}))))
    (testing "lt="
      (is (= [true {}]
             ((lt= :start :end) {:start start
                                 :end end})))
      (is (= [true {}]
             ((lt= :start :end) {:start start
                                 :end start})))
      (is (= [false #{":start is not less than or equal to :end"}]
             ((lt= :start :end) {:start end
                                 :end start}))))
    (testing "eq"
      (is (= [false #{":start is not equal to :end"}]
             ((eq :start :end) {:start start
                                :end end})))
      (is (= [true {}]
             ((eq :start :end) {:start start
                                :end start})))
      (is (= [false #{":start is not equal to :end"}]
             ((eq :start :end) {:start end
                                :end start}))))
    (testing "exception"
      (is (thrown-with-msg? java.lang.IllegalArgumentException
                            #"No implementation of method"
                            ((lt :start :end) {:start ""
                                               :end 1}))))))
