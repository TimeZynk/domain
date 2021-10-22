(ns com.timezynk.domain.validation
  "This namespace is copied from com.timezynk.domain.util.schema.validation."
  (:require
   [clojure.spec.alpha :as spec]
   [clojure.string :as str]
   [com.timezynk.domain.validation.compare :as compare]
   [com.timezynk.domain.validation.only-these :refer [only-these]]
   [com.timezynk.domain.validation.set :as set]
   [com.timezynk.domain.validation.validate :refer [validate-property validate-schema]]
   [com.timezynk.useful.date :as ud]
   [slingshot.slingshot :refer [throw+]]))

;; --------------------

(def all-of         set/all-of)
(def some-of        set/some-of)
(def none-of        set/none-of)
(def one-of         set/one-of)
(def has            set/has)
(def has-not        set/has-not)

;; --------------------

(def lt    compare/lt)
(def lt=   compare/lt=)
(def eq    compare/eq)

;; --------------------

(defn validate-properties [all-optional? properties doc]
  (let [only-these-keys (apply only-these (keys properties))
        rule            (->> properties
                             (map #(partial validate-property % all-optional?))
                             (cons only-these-keys)
                             (apply set/all-of))]
    (rule doc)))

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

      (when (< 0 (compare start end))
        (throw+ {:code 400
                 :message "The interval[end] parameter value has to be greater than or equal to interval[start]"
                 :error-code "INTERVAL_END_INVALID"}))

      (when (and match (not (spec/valid? ::match match)))
        (throw+ {:code 400
                 :message (str "Invalid value provided for interval[match] parameter. Valid values:" (str/join ", " match-param-values))
                 :error-code "INTERVAL_MATCH_INVALID"})))))

