(ns domain-core.validation
  "This namespace is copied from tzbackend.util.schema.validation."
  (:require [clojure.set                       :refer [union]]
            ;[clojure.tools.logging             :as log :refer [spy info warn]]
            [clojure.core.reducers             :as r]



            [slingshot.slingshot               :refer [throw+]]
            [clojure.set                       :refer [difference]]
            ;[validateur.validation             :as v]
            )
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
                                        ;(reduce union)
                    error-func)]))))

(defn all-of
  "All conditions should be true (AND)"
  [& rules]
  (validation-operator (fn [results] (reduce #(and % %2) results))
                       (fn [errs] {:and errs})
                       rules))

(defn- some-of-proto [treshold-func err-wrap-name rules]
  "Used internally by tzbackend.util.validate ns"
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
    [true #{}]
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
        [false {:invalid-attrs #{(str invalid-attrs " are invalid attributes on this entry")}}]))))

(defn presence-of
  "From validateur"
  [attribute & {:keys [message message-fn] :or {message "can't be blank"}}]
  (let [f (if (vector? attribute) get-in get)
        msg-fn (or message-fn (constantly message))]
    (fn [m]
      (let [value  (f m attribute)
            res    (and (not (nil? value))
                        (if (string? value)
                          (not (empty? (clojure.string/trim value))) true))
            msg (msg-fn :blank m attribute)
            errors (if res {} {attribute #{msg}})]
        [(empty? errors) errors]))))

(defn has [& properties]
  (fn [entry]
    (let [rule (->> properties
                    (map #(presence-of %))
                    (apply all-of))]
      (rule entry))))

(defn no-presence-of [attribute]
  (fn [entry]
    (let [[presence-of?] ((presence-of attribute) entry)]
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
      [false #{{:attr attr-name :error "required property has no value"}}]
      (if (fun val)
        [true #{}]
        [false #{{:attr attr-name :error msg}}]))))

(defn str->object-id? [s]
  (or (instance? ObjectId s)
      (re-find #"^[\da-f]{24}$" s)))
(defn str->time?      [s] (re-find #"^\d{2}:\d{2}(:\d{2})?(\.\d{3})?$" s))
(defn str->date-time? [s] (re-find #"^\d{4}\-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2})?(\.\d{3})?$" s))
(defn str->date?      [s] (re-find #"^\d{4}\-\d{2}-\d{2}$" s))

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

(defn boolean? [s] (or (true? s) (false? s)))

(defn validate-type [attr-name type-name]
  (get {:string    (check string?     attr-name "not a string")
        :number    (check number?     attr-name "not a number")
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
       [false #{(str "property '" (name attr-name) "' of unknown type '" (name type-name) "'")}]))

(defn validate-vector [all-optional? attr-name rule vector-value]
  (let [result (map (partial
                     validate-schema all-optional?
                     rule)
                    vector-value)
        valid? (if (empty? result)
                    true
                    (->> result
                         (map first)
                         (reduce #(and % %2))))]
    (if valid?
        [true {}]
        [false {attr-name result}])))

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
             (:default property-definition)
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
      (throw+ {:type    ::validation-error
               :message "The document have invalid properties."
               :errors  err-messages}))))

(defn validate-doc!
  "Validates document. Expects a packed document with validated properties. Throws an exception if the validation does not pass."
  [rule doc]
  (let [[valid? err-messages] (rule doc)]
    (when-not valid?
      (throw+ {:type    ::validation-error
               :message "The document is invalid."
               :errors  err-messages}))))
