(ns com.timezynk.domain.pack
  (:require [com.timezynk.domain.update-leafs :refer [update-leafs]]
            [clojure.core.reducers            :as r]
            [clojure.edn                      :as edn]
            [clojure.string                   :as s]
            [clojure.walk                     :refer [postwalk-replace postwalk]]))

                                       ;pack query

;; (def mongo-operators {:_from_ :$gte
;;                       :_to_   :$lte
;;                       :_lt_   :$lt
;;                       :_lte_  :$lte
;;                       :_gt_   :$gt
;;                       :_gte_  :$gte
;;                       :_or_   :$or
;;                       :_not_  :$ne
;;                       :_in_   :$in
;;                       :_elem_ :$elemMatch})

;; (defn- replace-with-mongo-operators [q]
;;   (postwalk-replace mongo-operators q))

(defn- operator? [key]
  (->> key name (re-matches #"^_.*_$")))

(defn- clean-property-path [path]
  (remove #(or (= [] %)
               (operator? %))
          path))

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
             (and head tail-head) (recur tail
                                         (conj path head :properties))
             head (recur tail
                         (conj path head :type))
             :else path)))))

(defn get-type [trail _ props]
  (get-in props (get-type-path trail)))

(defmulti pack-property get-type)

(defn- pack-query-parameters [q properties]
  (update-leafs q (fn [path value]
                    (pack-property (clean-property-path path)
                                   value
                                   properties))))

(defn pack-query [dom-type-factory request]
  (let [{:keys [domain-query-params route-params]} request
        {:keys [properties]}                       dom-type-factory]
    (-> (merge domain-query-params route-params)
        (pack-query-parameters properties)
        ;replace-with-mongo-operators
        )))

(defn pack-post-query [dom-type-factory request]
  (let [{:keys [domain-query-params route-params body-params]} request
        {:keys [properties]}                                   dom-type-factory]
    (-> (merge body-params domain-query-params route-params)
        (pack-query-parameters properties)
        ;replace-with-mongo-operators
        )))


                                        ; pack collects (remove this funcitonality?)

(defn- pack-collect [properties [property-name options]]
  (let [{:keys [collection
                domain-type?
                unpack
                vid
                only-keys
                target]
         :as collect-def} (get-in properties [property-name :collect])]
    [target
     (-> options
         (assoc :collection collection
                :domain-type? (if-not (nil? domain-type?)
                                domain-type?
                                true)
                :unpack unpack
                :versioned? (if vid true false)
                :only-keys (let [fields (:fields options)]
                             (if (= :all fields) [] fields))
                :ref-property (or vid property-name))
         (dissoc :fields))]))

(defn pack-collects [dom-type-factory request]
  (let [{:keys [domain-collect-params]} request
        {:keys [properties]} dom-type-factory]
    (->> domain-collect-params
         (map (partial pack-collect properties))
         (into {}))))

(defn filter-params [params properties flag]
  (apply dissoc params
    (map (fn [[k p]] k)
      (filter
        (fn [[k p]]
          (get p flag))
        properties))))


                                        ; pack body

(defn validate-input!
  "Validate input and ensure it can be packed/coerced"
  [dom-type-factory doc]
  ;; todo: Not written yet. Use -> json->pack named parameter of defproptype
  ;; Another possibility is to totally skip this step. It might not be necessary.
  ;; The only function would be to present a nicer error message.
  doc)

(defn pack-doc
  "Converts the document from a document with values in \"client types\",
   to a document with values in \"server types\"."
  [dom-type-factory doc]
  (update-leafs doc
                pack-property
                (:properties dom-type-factory)))

(defn pack-insert
  "Filters and prepares parameters before insert"
  [dom-type-factory req]
  (filter-params (merge (:body-params req) (:route-params req))
                 (:properties dom-type-factory)
                 :remove-on-create?))

(defn pack-bulk-insert
  "Filters and prepares parameters before bulk insert"
  [dom-type-factory req]
  (map
    (fn [d]
      (filter-params (merge d (:route-params req))
                 (:properties dom-type-factory)
                 :remove-on-create?))
    (:body-params req)))

(defn pack-update
  "Filters and prepares parameters before update"
  [dom-type-factory req]
  (filter-params (merge (:body-params req) (:route-params req))
                 (:properties dom-type-factory)
                 :remove-on-update?))


;; (defmethod pack-property :object-id [_ v props]
;;   (um/object-id v))

;; (defmethod pack-property :date [_ v props]
;;   (ud/->local-date v))

;; (defmethod pack-property :time [_ v props]
;;   (ud/->local-time v))

;; (defn get-type-path
;;   "From a path, created by update-leafs,
;;    create a path to use with get-in"
;;   [trail]
;;   (into []
;;         (loop [trail (into () trail)
;;                path  []]
;;           (let [[head & tail] trail
;;                 [tail-head]   tail]
;;             (cond
;;              (= [] tail-head) (let [tail (rest tail)]
;;                                 (recur tail
;;                                        (conj path head
;;                                              :children
;;                                              (if (seq tail) :properties :type))))
;;              head (recur tail
;;                          (conj path head :type))
;;              :else path)))))

;; (defmulti pack-property
;;   (fn [trail _ props]
;;     (get-in props (get-type-path trail))))

;; (defn pack-doc
;;   "Converts the document from a document with values in \"client types\",
;;    to a document with values in \"server types\"."
;;   [dom-type-factory doc]
;;   (update-leafs doc
;;                 pack-property
;;                 (:properties dom-type-factory)))

;; (defn- collect-property-query [props k v]
;;   (let [collect-spec (get-in props [k :collect])
;;         query-value  {:!collect {:collection  (get collect-spec :collection)
;;                                  :domain-type (get collect-spec :domain-type? true)
;;                                  :unpack      (get collect-spec :unpack)
;;                                  :only-keys   (->> (clojure.string/split v #"\;")
;;                                                    (map keyword)
;;                                                    (remove (partial = :true))
;;                                                    (into []))
;;                                  :ref-property k}}
;;         prop-name    (get collect-spec :target)]
;;     [prop-name, query-value]))

;; (defn- pack-operator [props [k v]]
;;   (let [[prop-name op]          (if (keyword? k)
;;                                   [k, :eq]
;;                                   (let [[_ n op] (or
;;                                                   (re-find #"^([a-z-_A-Z]+)\[([a-z]+)\]" k)
;;                                                   (re-find #"^([a-zA-Z0-9\-_]+)" k))]
;;                                     [(keyword n), (if-not (nil? op) (keyword op) :eq)]))
;;         packed-v                (if (or (= "null" v)
;;                                         (= "nil" v))
;;                                   nil
;;                                   (pack-property [prop-name] v props))
;;         [prop-name, prop-value] (case op
;;                                   :eq      [prop-name packed-v]
;;                                   :from    [prop-name {:$gte packed-v}]
;;                                   :to      [prop-name {:$lte packed-v}]
;;                                   :not     [prop-name {:$ne packed-v}]
;;                                   :collect (collect-property-query props prop-name v))]
;;     [prop-name, prop-value]))

;; (defn- merge-into-hashmap [v]
;;   (r/reduce (fn [m [k v]]
;;               (let [current-v (get m k)]
;;                 (if (map? current-v)
;;                   (update-in m [k] merge v)
;;                   (assoc m k v))))
;;             {}
;;             v))

;; (defn- merge-into-hashmaps [v]
;;   [(merge-into-hashmap (first v))
;;    (merge-into-hashmap (second v))])

;; (defn- split->query-and-collects [q-and-c]
;;   (let [collect? (fn [[_ v]]
;;                    (and (map? v)
;;                         (get v :!collect)))
;;         query    (r/remove collect? q-and-c)
;;         collects (->> q-and-c
;;                       (r/filter collect?)
;;                       (r/map (fn [[k v]]
;;                                [k (get v :!collect)])))]
;;     [query, collects]))

;; (defn pack-query-and-collects
;;   "Converts the merged url query parameters and route parameters to a query with
;;    values in server side types."
;;   [dom-type-factory req]
;;   (let [query (merge (:query-params req)
;;                      (:route-params req)
;;                      (-> req
;;                          :user
;;                          (select-keys [:company-id])))
;;         ]
;;     (->> query
;;          (map (partial pack-operator (:properties dom-type-factory)))
;;          split->query-and-collects
;;          merge-into-hashmaps)))

;; (defn filter-params [params properties flag]
;;   (apply dissoc params
;;     (map (fn [[k p]] k)
;;       (filter
;;         (fn [[k p]]
;;           (get p flag))
;;         properties))))

;; (defn pack-insert
;;   "Filters and prepares parameters before insert"
;;   [dom-type-factory req]
;;   (filter-params (merge (:body-params req) (:route-params req))
;;                  (:properties dom-type-factory)
;;                  :remove-on-create?))

;; (defn pack-update
;;   "Filters and prepares parameters before update"
;;   [dom-type-factory req]
;;   (filter-params (merge (:body-params req) (:route-params req))
;;                  (:properties dom-type-factory)
;;                  :remove-on-update?))

;; (defmethod pack-property :default [_ v props]
;;   ;; add a warning here? No pack-property function defined for :default type
;;   v)

;; (defmethod pack-property :any [_ v props] v)

;; (defmethod pack-property :string [_ v props]
;;   (when v
;;     (if (string? v)
;;       (let [^String trimmed (s/trim v)]
;;         (when-not (.isEmpty ^String trimmed) trimmed))
;;       (.toString v))))

;; (comment defmethod pack-property :object-id [_ v props]
;;   (um/object-id v))

;; (comment defmethod pack-property :date [_ v props]
;;   (ud/->local-date v))

;; (comment defmethod pack-property :time [_ v props]
;;   (ud/->local-time v))

;; ;; Todo: Remove the dependecy of joda
;; (defmethod pack-property :date-time [_ v props]
;;   ;(ud/->local-datetime v)
;;   (org.joda.time.LocalDateTime. v))

;; (defmethod pack-property :number [_ v props]
;;   (if (string? v)
;;     (edn/read-string v)
;;     v))

;; (defmethod pack-property :timestamp [_ v props]
;;   (if (string? v)
;;     (Long/parseLong v)
;;     v))

;; (defmethod pack-property :boolean [_ v props]
;;   (if (string? v)
;;     (Boolean/parseBoolean v)
;;     v))

;; (defmethod pack-property nil [[k] v props]
;;   ;;todo how to handle warnings?
;;   ;;(warn "Packing nil property" k "with options" props)
;;   v)
