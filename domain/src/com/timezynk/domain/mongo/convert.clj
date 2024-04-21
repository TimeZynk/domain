(ns com.timezynk.domain.mongo.convert
  (:require
   [com.timezynk.useful.date :as date])
  (:import [org.bson BsonType]
           [org.bson.codecs Codec]))

(defn joda-datetime-codec []
  (reify Codec
    (decode [_this _reader _decoder-context]
      ())

    (encode [_this writer value _encoder-context]
      (.writeString writer (.toString value)))

    (getEncoderClass [_this]
      org.joda.time.DateTime)))

(defn joda-localdate-codec []
  (reify Codec
    (decode [_this _reader _decoder-context]
      ())

    (encode [_this writer value _encoder-context]
      (.writeString writer (.toString value)))

    (getEncoderClass [_this]
      org.joda.time.LocalDate)))

(defn joda-localdatetime-codec []
  (reify Codec
    (decode [_this _reader _decoder-context]
      ())

    (encode [_this writer value _encoder-context]
      (.writeString writer (.toString value)))

    (getEncoderClass [_this]
      org.joda.time.LocalDateTime)))

(defn joda-localtime-codec []
  (reify Codec
    (decode [_this _reader _decoder-context]
      ())

    (encode [_this writer value _encoder-context]
      (.writeString writer (.toString value)))

    (getEncoderClass [_this]
      org.joda.time.LocalTime)))

(defn java-time-localdate-codec []
  (reify Codec
    (decode [_this _reader _decoder-context]
      ())

    (encode [_this writer value _encoder-context]
      (.writeString writer (.toString value)))

    (getEncoderClass [_this]
      java.time.LocalDate)))

(defn java-time-localtime-codec []
  (reify Codec
    (decode [_this _reader _decoder-context]
      ())

    (encode [_this writer value _encoder-context]
      (.writeString writer (.toString value)))

    (getEncoderClass [_this]
      java.time.LocalTime)))

(defn java-time-localdatetime-codec []
  (reify Codec
    (decode [_this _reader _decoder-context]
      ())

    (encode [_this writer value _encoder-context]
      (.writeDatetime writer (date/to-millis value)))

    (getEncoderClass [_this]
      java.time.LocalDateTime)))

(defn java-time-zoneddatetime-codec []
  (reify Codec
    (decode [_this reader _decoder-context]
      (date/to-datetime (.readDatetime reader)))

    (encode [_this writer value _encoder-context]
      (.writeDatetime writer (date/to-millis value)))

    (getEncoderClass [_this]
      java.time.ZonedDateTime)))

(defonce new-codecs [(joda-datetime-codec)
                     (joda-localdate-codec)
                     (joda-localdatetime-codec)
                     (joda-localtime-codec)
                     (java-time-localdate-codec)
                     (java-time-localtime-codec)
                     (java-time-localdatetime-codec)
                     (java-time-zoneddatetime-codec)])

(defonce new-types {BsonType/DATE_TIME java.time.ZonedDateTime})
