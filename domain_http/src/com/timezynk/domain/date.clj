(ns com.timezynk.domain.date
  (:import [org.joda.time DateTimeZone]
           [org.joda.time.format DateTimeFormat DateTimeFormatter]))

(def ^DateTimeFormatter rfc-1123-formatter
  (.. (DateTimeFormat/forPattern "EEE, dd MMM yyyy HH:mm:ss 'GMT'")
    (withLocale java.util.Locale/US)
    (withZone DateTimeZone/UTC)))

(defn parse-rfc-1123
  "Parse date in RFC 1123 format"
  [s]
  (when s
    (.parseDateTime rfc-1123-formatter s)))

(defn to-rfc-1123
  "Convert to RFC 1123 formatted date"
  [d]
  (when d
    (.print rfc-1123-formatter d)))
