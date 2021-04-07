(ns com.timezynk.domain.pack
  (:require
   [clojure.core.reducers :as r]
   [clojure.edn :as edn]
   [clojure.string :as s]
   [clojure.walk :refer [postwalk-replace]]
   [com.timezynk.domain.update-leafs :refer [update-leafs]]
   [com.timezynk.useful.date :as ud]
   [com.timezynk.useful.mongo :as um :refer [object-id? intersecting-query start-inside-period-query]]
   [slingshot.slingshot :refer [throw+]]))

                                       ;pack query


(def mongo-operators {:_from_ :$gte
                      :_to_   :$lte
                      :_lt_   :$lt
                      :_lte_  :$lte
                      :_gt_   :$gt
                      :_gte_  :$gte
                      :_or_   :$or
                      :_not_  :$ne
                      :_in_   :$in
                      :_elem_ :$elemMatch})

(defn- replace-with-mongo-operators [q]
  (postwalk-replace mongo-operators q))

(defn- operator? [key]
  (or
   (= [] key)
   (->> key name (re-matches #"^_.*_$"))))

(defn- clean-property-path [path]
  (remove operator? path))

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
              (and (keyword? head)
                   (s/includes? (name head) "."))
              (let [raw-trail (-> head
                                  (name)
                                  (s/split #"\."))
                    next-trail (->> raw-trail
                                    (r/map keyword)
                                    (into []))]
                (recur next-trail
                       path))
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

(defn interval-query [{:keys [start _start_ end _end_ criteria _match_]}]
  (if (= "intersects" (or criteria _match_))
    (intersecting-query (or start _start_) (or end _end_))
    (start-inside-period-query (or start _start_) (or end _end_))))

(defn pack-interval-query [q]
  (if (map? (:interval q))
    (merge
     (dissoc q :interval)
     (interval-query (:interval q)))
    q))

(defn pack-query [dom-type-collection request]
  (let [{:keys [domain-query-params route-params]} request
        {:keys [properties]}          dom-type-collection]
    (-> (merge domain-query-params route-params)
        (pack-query-parameters properties)
        (replace-with-mongo-operators)
        (pack-interval-query))))

(defn pack-post-query [dom-type-collection request]
  (let [{:keys [domain-query-params route-params body-params]} request
        {:keys [properties]}          dom-type-collection]
    (-> (merge body-params domain-query-params route-params)
        (pack-query-parameters properties)
        (replace-with-mongo-operators))))

                                       ; pack collects


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

(defn pack-collects [dom-type-collection request]
  (let [{:keys [domain-collect-params]} request
        {:keys [properties]} dom-type-collection]
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


(defn pack-doc
  "Converts the document from a document with values in \"client types\",
   to a document with values in \"server types\"."
  [dom-type-collection doc]
  (update-leafs doc
                pack-property
                (:properties dom-type-collection)))

(defn pack-insert
  "Filters and prepares parameters before insert"
  [dom-type-collection req]
  (filter-params (merge (:body-params req) (:route-params req))
                 (:properties dom-type-collection)
                 :remove-on-create?))

(defn pack-bulk-insert
  "Filters and prepares parameters before bulk insert"
  [dom-type-collection req]
  (map
   (fn [d]
     (filter-params (merge d (:route-params req) (:domain-query-params req))
                    (:properties dom-type-collection)
                    :remove-on-create?))
   (:body-params req)))

(defn pack-update
  "Filters and prepares parameters before update"
  [dom-type-collection req]
  (filter-params (merge (:body-params req) (:route-params req))
                 (:properties dom-type-collection)
                 :remove-on-update?))

(defn pack-values
  "Pack dynamic values so that they can be used without overwriting"
  [values]
  (r/reduce
   (fn [acc k v]
     (assoc acc (keyword (str "values." (name k))) v))
   {} values))

(defmethod pack-property :any [_ v props] v)

(defmethod pack-property :string [_ v props]
  (when v
    (if (string? v)
      (let [^String trimmed (s/trim v)]
        (when-not (.isEmpty ^String trimmed) trimmed))
      (.toString v))))

(defmethod pack-property :object-id [_ v props]
  (when (object-id? v)
    (um/object-id v)))

(defmethod pack-property :date [trail v props]
  (try
    (ud/->local-date v)
    (catch Exception e
      (throw+
       {:code 400
        :message (str "Parse error at path [\"" (s/join "\", \"" (map name trail)) "\"]. " (.getMessage e))}))))

(defmethod pack-property :time [trail v props]
  (try
    (ud/->local-time v)
    (catch Exception e
      (throw+
       {:code 400
        :message (str "Parse error at path [\"" (s/join "\", \"" (map name trail)) "\"]. " (.getMessage e))}))))

(defmethod pack-property :date-time [trail v props]
  (try
    (ud/->local-datetime v)
    (catch Exception e
      (throw+
       {:code 400
        :message (str "Parse error at path [\"" (s/join "\", \"" (map name trail)) "\"]. " (.getMessage e))}))))

(defmethod pack-property :number [_ v props]
  (if (string? v)
    (edn/read-string v)
    (if (ratio? v)
      (double v)
      v)))

(defmethod pack-property :duration [_ v props]
  (if (string? v)
    (edn/read-string v)
    (if (ratio? v)
      (double v)
      v)))

(defmethod pack-property :timestamp [_ v props]
  (let [parsed (if (string? v) (edn/read-string v) v)]
    ; Accept false and turn it into nil for backwards compatibility on the archived field
    (if (not parsed) nil parsed)))

(defmethod pack-property :boolean [trail v props]
  (if (string? v)
    (edn/read-string v)
    (if v true v)))

(defmethod pack-property :vector [trail v props]
  (if (sequential? v)
    v
    (pack-property (conj trail []) v props)))

(defmethod pack-property :map [_ v props]
  v)

(defmethod pack-property nil [trail v props]
  v)
