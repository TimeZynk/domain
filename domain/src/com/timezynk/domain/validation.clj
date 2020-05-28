(ns com.timezynk.domain.validation
  "This namespace is copied from com.timezynk.domain.util.schema.validation."
  (:require
   [clojure.core.reducers :as r]
   [clojure.set :refer [difference]]
   [clojure.spec.alpha :as spec]
   [clojure.string :as str]
   [com.timezynk.useful.date :as ud]
   [slingshot.slingshot :refer [throw+]]
   [validateur.validation :as v])
  (:import [org.bson.types ObjectId]
           [org.joda.time LocalDateTime LocalDate LocalTime]))

(declare validate-schema)

;; Operators ;;

(defn- validation-operator [operator-func error-func rules]
  (fn [entry]
    (let [results (map #(% entry) rules)
          valid?  (->> results
                       (map first)
                       operator-func)]
      (if valid?
        [true {}]
        [false (->> results
                    (map second)
                    error-func)]))))

(defn all-of
  "All conditions should be true (AND)"
  [& rules]
  (validation-operator (fn [results] (reduce #(and % %2) results))
                       (fn [errs] (apply merge errs))
                       rules))

(defn- some-of-proto [treshold-func err-wrap-name rules]
  "Used internally by com.timezynk.domain.util.validate ns"
  (validation-operator (fn [results] (->> results
                                          (filter true?)
                                          count
                                          treshold-func))
                       (fn [errs] {err-wrap-name errs})
                       rules))

(defn some-of
  "At least one condition should be true (OR)"
  [& rules]
  (some-of-proto #(> 0 %) :or rules))

(defn none-of
  "No condition should be true"
  [& rules]
  (some-of-proto zero? :none rules))

(defn one-of
  "One condition should be true (XOR)"
  [& rules]
  (some-of-proto #(= 1 %) :xor rules))


;;;;;;;;;;;;; Compare ;;;;;;;;;;;

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

;;;;;;;;;;;; handy ;;;;;;;;;;;;

(defn only-these
  "only these are valid attributes"
  [& valid-attributes]
  (fn [entry]
    (let [invalid-attrs (if-not (nil? valid-attributes)
                          (-> (keys entry)
                              set
                              (difference (into #{} valid-attributes))))]
      (if (empty? invalid-attrs)
        [true {}]
        [false (apply merge (map (fn [attr] {attr "invalid attribute"}) invalid-attrs))]))))

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

(defn check [fun attr-name msg]
  (fn [val]
    (if (nil? val)
      [false {attr-name "required property has no value"}]
      (if (fun val)
        [true {}]
        [false {attr-name msg}]))))

(defn object-id? [x]
  (isa? (class x) ObjectId))

(defn time? [x]
  (isa? (class x) LocalTime))

(defn date-time? [x]
  (isa? (class x) LocalDateTime))

(defn date? [x]
  (isa? (class x) LocalDate))

(defn timestamp? [x]
  (and (number? x) (<= 0 x)))

(defn validate-type [attr-name type-name]
  (get {:string    (check string?     attr-name "not a string")
        :number    (check number?     attr-name "not a number")
        :duration  (check number?     attr-name "not a duration in milliseconds")
        :vector    (check sequential? attr-name "not sequential")
        :map       (check map?        attr-name "not a map")
        :time      (check time?       attr-name "not a valid time declaration")
        :date-time (check date-time?  attr-name "not a valid date-time declaration")
        :date      (check date?       attr-name "not a valid date")
        :timestamp (check timestamp?  attr-name "not a valid timestamp")
        :object-id (check object-id?  attr-name "not a valid id")
        :boolean   (check boolean?    attr-name "not a boolean")
        :any       (fn [_] [true #{}])}
       type-name
       [false {attr-name {"unknown type" (name type-name)}}]))

(defn validate-vector [all-optional? attr-name rule vector-value]
  (if (sequential? vector-value)
    (let [[valid? errors] (r/reduce
                           (fn [acc v]
                             (let [[v? e] (validate-schema all-optional? rule v)]
                               [(and (first acc) v?)
                                (merge (second acc) e)]))
                           [true {}]
                           vector-value)]
      [valid? {attr-name errors}])
    [false {attr-name {:vector "not sequential"}}]))

(defn get-check-fn [all-optional? k property-name property-definition]

  (case k
    :min        (check #(<= property-definition %)
                       property-name
                       (str "smaller than " property-definition))
    :max        (check #(>= property-definition %)
                       property-name
                       (str "bigger than " property-definition))
    :type       (validate-type property-name property-definition)
    ;;:vector    (partial validate-vector property-name property-definition)
    :properties #(validate-schema all-optional? {:properties property-definition} %)
    ;;:children   (fn [v] (map #(#'validate-schema property-definition %) v))
    :children   (partial validate-vector all-optional? property-name property-definition)
    ;;:map        property-definition
    :in         (check #(contains? property-definition %)
                       property-name
                       (str "not in " property-definition))))

;; todo: this is UGLY! this should not be done here...
(defn escape-optional? [property property-value all-optional?]
  (let [[property-name property-definition] property]
    (and (or all-optional?
             (:optional? property-definition)
             (not (nil? (:default property-definition)))
             (:computed property-definition)
             (:derived property-definition))

         (nil? (property-name property-value)))))

(def validation-keys [:min
                      :max
                      :type
                      :properties
                      :children
                      :in])

(defn validate-property [property all-optional? val]
  (if (escape-optional? property val all-optional?)
    [true {}]
    (let [[property-name property-definition] property
          property-definition                 (select-keys property-definition validation-keys)
          property-value                      (get val property-name)
          validator                           (->> (dissoc property-definition :optional?)
                                                   (map (fn [[k v]]
                                                          (get-check-fn all-optional? k property-name v)))
                                                   (apply all-of))]
      (validator property-value))))

(defn validate-properties [all-optional? properties doc]
  (let [only-these-keys (apply only-these (keys properties))
        rule            (->> properties
                             (map #(partial validate-property % all-optional?))
                             (cons only-these-keys)
                             (apply all-of))]
    (rule doc)))

(defn validate-schema
  "Deprecated"
  [all-optional? schema m]
  (let [{:keys [properties after-pack-rule]} schema
        only-these-keys                      (apply only-these (keys properties))
        rule                                 (->> properties
                                                  (map #(partial validate-property % all-optional?))
                                                  (cons only-these-keys)
                                                  (apply all-of))]
    (rule m)))

(defn validate-schema!
  "Deprecated. Validates and pack. Throws an exception if the validation is not passed"
  ;;Todo: It's ugly! refactor as a monad?
  [all-optional? pack-params schema exclude-from-validation params]
  (let [errs                  (validate-schema all-optional?
                                               schema
                                               (apply dissoc params exclude-from-validation))
        [valid? err-messages] errs
        after-pack-rule       (get schema :after-pack-rule)]
    (if valid?
      (let [packed                  (pack-params schema params)
            [valid2? err-messages2] (after-pack-rule packed)]
        (if valid2?
          packed
          (throw+ {:code    400
                   :error   :validation
                   :message {:params params
                             :errors err-messages2}})))
      (throw+ {:code    400
               :error   :validation
               :message {:params params
                         :errors err-messages}}))))

(defn validate-json-input!
  "Validates each property. Expects to validate if a json structure can be packed."
  [properties doc]
  :todo)

(defn validate-properties!
  "Validates each property. Expects a packed doc..
   Throws an exception if the validation does not pass."
  [all-optional? properties doc]
  (let [errs                  (validate-properties all-optional?
                                                   properties
                                                   doc)
        [valid? err-messages] errs]
    (when-not valid?
      (throw+ {:type    :validation-error
               :message "The document have invalid properties."
               :document doc
               :errors  err-messages}))))

(defn validate-doc!
  "Validates document. Expects a packed document with validated properties. Throws an exception if the validation does not pass."
  [rule doc]
  (let [[valid? err-messages] (rule doc)]
    (when-not valid?
      (throw+ {:type    :validation-error
               :message "The document is invalid."
               :errors  err-messages}))))

(defn- valid-date-string? [v]
  (or (ud/date-string? v) (ud/date-time-string? v)))

(def match-param-values #{"start-in" "intersects"})
(spec/def ::start valid-date-string?)
(spec/def ::end valid-date-string?)
(spec/def ::match #(contains? match-param-values %))

(defn validate-interval-params! [{:keys [params]}]
  (when-let [interval (:interval params)]
    (let [start (:start interval)
          end (:end interval)
          match (:match interval)]

    (when-not (spec/valid? ::start start)
      (throw+ {:code 400
               :message "When the interval parameter is used the interval[start] parameter is required and has to be a valid date string"
               :error-code "INTERVAL_START_INVALID"}))

    (when-not (spec/valid? ::end end)
      (throw+ {:code 400
               :message "When the interval parameter is used the interval[end] parameter is required and has to be a valid date string"
               :error-code "INTERVAL_END_INVALID"}))

    (when (< 0 (compare start end) )
      (throw+ {:code 400
               :message "The interval[end] parameter value has to be greater than or equal to interval[start]"
               :error-code "INTERVAL_END_INVALID"}))

    (when (and match (not (spec/valid? ::match match)))
      (throw+ {:code 400
               :message (str "Invalid value provided for interval[match] parameter. Valid values:" (str/join ", " match-param-values))
               :error-code "INTERVAL_MATCH_INVALID"})))))

