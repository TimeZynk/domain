(ns com.timezynk.domain.pack
  (:require [com.timezynk.domain.update-leafs :refer [update-leafs]]
   [clojure.core.reducers             :as r]
   [clojure.edn                       :as edn]
   [clojure.string                    :as s]))

(defn get-type-path
  "From a path, created by update-leafs,
   create a path to use with get-in"
  [trail]
  (into []
        (loop [trail (into () trail)
               path  []]
          (let [[head & tail] trail
                [tail-head]   tail]
            (cond
             (= [] tail-head) (let [tail (rest tail)]
                                (recur tail
                                       (conj path head
                                             :children
                                             (if (seq tail) :properties :type))))
             head (recur tail
                         (conj path head :type))
             :else path)))))

(defmulti pack-property
  (fn [trail _ props]
    (get-in props (get-type-path trail))))

(defn pack-doc
  "Converts the document from a document with values in \"client types\",
   to a document with values in \"server types\"."
  [dom-type-collection doc]
  (update-leafs doc
                pack-property
                (:properties dom-type-collection)))

(defn- collect-property-query [props k v]
  (let [collect-spec (get-in props [k :collect])
        query-value  {:!collect {:collection  (get collect-spec :collection)
                                 :domain-type (get collect-spec :domain-type? true)
                                 :unpack      (get collect-spec :unpack)
                                 :only-keys   (->> (clojure.string/split v #"\;")
                                                   (map keyword)
                                                   (remove (partial = :true))
                                                   (into []))
                                 :ref-property k}}
        prop-name    (get collect-spec :target)]
    [prop-name, query-value]))

(defn- pack-operator [props [k v]]
  (let [[prop-name op]          (if (keyword? k)
                                  [k, :eq]
                                  (let [[_ n op] (or
                                                  (re-find #"^([a-z-_A-Z]+)\[([a-z]+)\]" k)
                                                  (re-find #"^([a-zA-Z0-9\-_]+)" k))]
                                    [(keyword n), (if-not (nil? op) (keyword op) :eq)]))
        packed-v                (if (or (= "null" v)
                                        (= "nil" v))
                                  nil
                                  (pack-property [prop-name] v props))
        [prop-name, prop-value] (case op
                                  :eq      [prop-name packed-v]
                                  :from    [prop-name {:$gte packed-v}]
                                  :to      [prop-name {:$lte packed-v}]
                                  :not     [prop-name {:$ne packed-v}]
                                  :collect (collect-property-query props prop-name v))]
    [prop-name, prop-value]))

(defn- merge-into-hashmap [v]
  (r/reduce (fn [m [k v]]
              (let [current-v (get m k)]
                (if (map? current-v)
                  (update-in m [k] merge v)
                  (assoc m k v))))
            {}
            v))

(defn- merge-into-hashmaps [v]
  [(merge-into-hashmap (first v))
   (merge-into-hashmap (second v))])

(defn- split->query-and-collects [q-and-c]
  (let [collect? (fn [[_ v]]
                   (and (map? v)
                        (get v :!collect)))
        query    (r/remove collect? q-and-c)
        collects (->> q-and-c
                      (r/filter collect?)
                      (r/map (fn [[k v]]
                               [k (get v :!collect)])))]
    [query, collects]))

(defn pack-query-and-collects
  "Converts the merged url query parameters and route parameters to a query with
   values in server side types."
  [dom-type-collection req]
  (let [query (merge (:query-params req)
                     (:route-params req)
                     (-> req
                         :user
                         (select-keys [:company-id])))
        ]
    (->> query
         (map (partial pack-operator (:properties dom-type-collection)))
         split->query-and-collects
         merge-into-hashmaps)))

(defn filter-params [params properties flag]
  (apply dissoc params
    (map (fn [[k p]] k)
      (filter
        (fn [[k p]]
          (get p flag))
        properties))))

(defn pack-insert
  "Filters and prepares parameters before insert"
  [dom-type-collection req]
  (filter-params (merge (:body-params req) (:route-params req))
                 (:properties dom-type-collection)
                 :remove-on-create?))

(defn pack-update
  "Filters and prepares parameters before update"
  [dom-type-collection req]
  (filter-params (merge (:body-params req) (:route-params req))
                 (:properties dom-type-collection)
                 :remove-on-update?))

(defmethod pack-property :default [_ v props]
  ;; add a warning here? No pack-property function defined for :default type
  v)

(defmethod pack-property :any [_ v props] v)

(defmethod pack-property :string [_ v props]
  (when v
    (if (string? v)
      (let [^String trimmed (s/trim v)]
        (when-not (.isEmpty ^String trimmed) trimmed))
      (.toString v))))

(comment defmethod pack-property :object-id [_ v props]
  (um/object-id v))

(comment defmethod pack-property :date [_ v props]
  (ud/->local-date v))

(comment defmethod pack-property :time [_ v props]
  (ud/->local-time v))

(comment defmethod pack-property :date-time [_ v props]
  (ud/->local-datetime v))

(defmethod pack-property :number [_ v props]
  (if (string? v)
    (edn/read-string v)
    v))

(defmethod pack-property :timestamp [_ v props]
  (if (string? v)
    (Long/parseLong v)
    v))

(defmethod pack-property :boolean [_ v props]
  (if (string? v)
    (Boolean/parseBoolean v)
    v))

(defmethod pack-property nil [[k] v props]
  ;;todo how to handle warnings?
  ;;(warn "Packing nil property" k "with options" props)
  v)
