(ns com.timezynk.domain.schema
  (:refer-clojure :exclude [map vector string number boolean time])
  (:require
   [clojure.core :as c]
   [clojure.core.reducers :as r]
   [clojure.spec.alpha :as spec]
   [clojure.string :refer [trim]]
   [com.timezynk.useful.mongo :as um]
   [tzbackend.domain.update-leafs :refer [update-leafs]]
   [com.timezynk.useful.rest.current-user :as current-session]
   )
  (:import [org.bson.types ObjectId]))

(def ^:const NAME_MIN_BYTES 1)
(def ^:const NAME_MAX_BYTES 64)

(defn t [type]
  (fn [& {:as options}]
    (merge {:type type} options)))

(def id (t :object-id))

(defn auto-id [& options]
  (apply id :default (fn [_] (ObjectId.)) options))

(def string    (t :string))
(def number    (t :number))
(def boolean   (t :boolean))
(def date      (t :date))
(def date-time (t :date-time))
(def time      (t :time))
(def timestamp (t :timestamp))
(def duration  (t :duration))
(def any       (t :any))

(defn vector [children & options]
  (apply (t :vector) :children children options))

(defn map [properties & options]
  (apply (t :map) :properties properties options))

(defn maps [properties & options]
  (apply vector (map properties)
         options))

;; Yep, this one is strange
(def backend-id (ObjectId. "52dd44373004b346e641112c"))

(def default-properties {:id         (id :optional? true)
                         :vid        (id :optional? true)
                         :archived   (timestamp :optional? true)
                         :pid        (id :optional? true
                                         :remove-on-create? true)
                         :valid-from (timestamp :optional? true
                                                :remove-on-create? true
                                                :remove-on-update? true)
                         :valid-to   (timestamp :optional? true
                                                :remove-on-create? true
                                                :remove-on-update? true)
                         :created-ts (timestamp :optional? true)
                         :created    (timestamp :computed (fn [doc]
                                                            (or (:created-ts doc)
                                                                (when (:id doc)
                                                                  (.getTime ^ObjectId (:id doc)))))
                                                :optional? true
                                                :remove-on-create? true
                                                :remove-on-update? true)
                         :created-by (id :derived (fn [doc update?]
                                                    (when-not update?
                                                      (or (current-session/user-id)
                                                          (:created-by doc)
                                                          backend-id))))
                         :changed-by (id :derived (fn [doc update?]
                                                    (when update?
                                                      (or (current-session/user-id)
                                                          (:changed-by doc)
                                                          backend-id))))
                         :company-id (id)})

(defn make-all-optional [props]
  (r/reduce (fn [props prop-name prop-def]
              (assoc props prop-name
                     (assoc prop-def :optional? true)))
            {}
            props))

(defn domain-type [domain-type-collection & options]
  (apply map
         (-> (get domain-type-collection :properties)
             make-all-optional)
         options))

(defn create-schema [schema]
  (-> schema
      (update-in [:properties]
                 (fn [props]
                   (merge default-properties props)))
      (update-in [:after-pack-rule]
                 (fn [rule]
                   (or rule (constantly [true #{}]))))))

(defmacro defschema [name
                     description
                     domain-type-name
                     collection
                     persistence-layer
                     properties
                     & [after-pack-rule]]
  (let [name name]
    `(def ~name (create-schema {:name              ~domain-type-name
                                :description       ~description
                                :persistence-layer ~persistence-layer
                                :collection        ~collection
                                :properties        ~properties
                                :after-pack-rule   ~after-pack-rule}))))

(defn- function?
  "Returns true if argument is a function or a symbol that resolves to
  a function (not a macro)."
  {:added "1.1"}
  [x]
  (if (symbol? x)
    (when-let [v (resolve x)]
      (and (fn? v)
           (not (:macro (meta v)))))
    (fn? x)))

(defn- replace-function [_ v]
  (if (function? v) (str v) v))

(defn render-schema "Prepares the schema to be serialized into JSON"
  [schema]
  (update-leafs schema replace-function))

(defn email? [str]
  (and str (string? str) (re-matches #"\S+@\S+" str)))

(defn valid-name? [n]
  (or (nil? n)
      (and (string? n) (<= NAME_MIN_BYTES (count (.getBytes (trim n) "UTF-8")) NAME_MAX_BYTES))))

(spec/def ::company-id um/object-id?)
(spec/def ::id um/object-id?)
(spec/def ::vid um/object-id?)
(spec/def ::name valid-name?)
(spec/def ::email email?)
