(ns com.timezynk.domain.schema.convert
  (:require [com.timezynk.useful.mongo :as um])
  (:import [java.time LocalDate ZonedDateTime]))

(defmulti nudge
  "Attempts to convert `value` to the type specified in `property-definition`.
   Returns `value` if no safe conversion exists."
  (fn [value property-definition]
    (let [from (type value)
          to (:type property-definition)
          method-map (methods nudge)]
      (if (method-map [from to])
        [from to]
        [:_ to]))))

(defmethod nudge :default
  [value _]
  value)

(defn- try-parse
  "Parses `values` through `parser-fn` swallowing all exceptions.
   `nil` if parsing fails.
   If `compare?` is set, `nil` unless the stringified result equals to `value`."
  [value parser-fn & {:keys [compare?]}]
  (let [parsed (try
                 (parser-fn value)
                 (catch Exception _ nil))]
    (if compare?
      (when (= value (str parsed)) parsed)
      parsed)))

(defmethod nudge [String :number]
  [value _]
  (let [try-parse (partial try-parse value)]
    (or (try-parse #(Integer/parseInt %))
        (try-parse #(Long/parseLong %))
        (try-parse bigint)
        (try-parse #(Float/parseFloat %) :compare? true)
        (try-parse #(Double/parseDouble %) :compare? true)
        (try-parse #(BigDecimal. %) :compare? true)
        value)))

(defmethod nudge [:_ :boolean]
  [value _]
  (boolean value))

(defmethod nudge [:_ :object-id]
  [value _]
  (or (try-parse value um/object-id)
      value))

(defmethod nudge [String :date]
  [value _]
  (or (try-parse value #(LocalDate/parse %))
      value))

(defmethod nudge [String :date-time]
  [value _]
  (or (try-parse value #(ZonedDateTime/parse %))
      value))
