(ns com.timezynk.domain.core
  (:require
   [clojure.core.reducers :as r]
   [com.timezynk.assembly-line :as line :refer [assembly-line]]
   [com.timezynk.domain.context :as context]
   [com.timezynk.domain.mask :as mask]
   [com.timezynk.domain.mongo.core :as m]
   [com.timezynk.domain.pack :as pack]
   [com.timezynk.domain.persistence :as p]
   [com.timezynk.domain.schema :as s]
   [com.timezynk.domain.update-leafs :refer [update-leafs-via-directive]]
   [com.timezynk.domain.validation :as v]
   [com.timezynk.useful.cancan :as ability]
   [com.timezynk.useful.date :as date]
   [com.timezynk.useful.rest :refer [json-response etag-response]]
   [compojure.core :refer [routes GET POST PUT PATCH DELETE]]
   [slingshot.slingshot :refer [throw+]]
   clojure.string)
  (:import [org.joda.time DateTime]))


                                        ;Aliases


(defmacro where [clause]
  (m/where* clause))

(def execute! line/execute!)

(def add-stations line/add-stations)

(defrecord DomainTypeCollection [natural-names description name version
                                 collection collects properties validate-doc old-docs
                                 update!-line insert!-line fetch-line destroy!-line
                                 count-line skip-logging]

  p/Persistence
  (p/select [this]
    (-> (fetch-line this)
        (line/prepare nil)))

  (p/select [this predicate]
    (p/select this predicate nil))

  (p/select [this predicate collects]
    (let [this (-> this
                   (update-in [:collection :restriction] merge predicate)
                   (update-in [:collects] merge collects))]
      (-> (fetch-line this)
          (line/prepare nil))))

  (p/select-count [this]
    (-> (count-line this) (line/prepare nil)))

  (p/select-count [this predicate]
    (let [this (update-in this [:collection :restriction] merge predicate)]
      (-> (count-line this) (line/prepare nil))))

  (p/conj! [this]
    (insert!-line this))

  (p/conj! [this records]
    (-> (p/conj! this)
        (line/prepare records)))

  ;; todo! We have to handle updates of the environment of an assembly line
  (p/disj! [this]
    (-> (destroy!-line this)
        (line/prepare nil)) ; todo: this one should not be needed.
    )

  (p/disj! [this predicate]
    (-> (destroy!-line (update-in this [:collection :restriction] merge predicate))
        (line/prepare nil)))

  ;; todo! We have to handle updates of the environment of an assembly line
  (p/update-in! [this]
    (update!-line this))

  (p/update-in! [this predicate]
    (update!-line (update-in this [:collection :restriction] merge predicate)))

  (p/update-in! [this predicate record]
    (-> (p/update-in! this predicate)
        (line/prepare record))))

(def collection-name
  "Creates a collection name with optional version number and where - has been replaced by ."
  (memoize
   (fn [dtc]
     (-> (str (-> (:name dtc)
                  name
                  (clojure.string/replace #"\-" (constantly ".")))
              (get dtc :version ""))
         keyword))))

(defmethod print-method DomainTypeCollection [dtc ^java.io.Writer w]
  (let [p (fn [& args] (.write w (apply str args)))]
    (p "DomainTypeCollection["
       (name (collection-name dtc))
       "]")))

                                        ; AssemblyLine station steps

(defn validate-doc-input!
  "Validate the format of the json doc values, and assures they can be converted to the
   property type on the server side?"
  [{:keys [properties]} doc]
  (v/validate-json-input! properties doc)
  doc)

(defn validate-id-availability [dtc doc]
  (let [docs (if (map? doc) [doc] doc)
        ids (as-> docs v
                  (r/map :id v)
                  (into #{} v)
                  (disj v nil))]

    (when (and (seq ids) (pos? @(m/select-count (:collection dtc) {:id {:$in ids}})))
      (throw+ {:code 409
               :type :conflict
               :errors [(str  "Conflict. Id already exists in " (-> dtc :name name) " collection")]}))
    doc))

(defn pack-doc
  "Convert the document values, from json types to server types."
  [collection doc]
  (pack/pack-doc collection doc))

(defn validate-doc! [{:keys [validate-doc]} doc]
  (when validate-doc
    (v/validate-doc! validate-doc doc))
  doc)

(defn validate-properties! [all-optional? {:keys [_name properties]} doc]
  (v/validate-properties! all-optional? properties doc)
  doc)

(defn walk-schema [trail prop-spec _doc]
  (case (:type prop-spec)
    :vector [(conj trail []), (get-in prop-spec [:children :properties])]
    :map    [trail, (:properties prop-spec)]
    [trail]))

(defn walk-schema-with-stop [f]
  (fn [trail prop-spec doc]
    (if (f prop-spec)
      [trail]
      (walk-schema trail prop-spec doc))))

(defn validate-properties2!
  "Extra validation, run the :validate function"
  [{:keys [_name properties]} doc]
  (update-leafs-via-directive properties
                              (walk-schema-with-stop :validate)
                              doc
                              (fn [[prop-name] prop-spec v doc]
                                (when-let [validate-fn (get prop-spec :validate)]
                                  (when-let [errs (validate-fn v doc)]
                                    (throw+ {:type     :validation-error
                                             :property prop-name
                                             :errors   errs})))
                                v))
  doc)

(defn add-default-values
  "Like add-derived-values, but only when the doc is created"
  [{:keys [properties]} doc]
  (update-leafs-via-directive properties
                              (walk-schema-with-stop :default)
                              doc
                              (fn [_ prop-spec v doc]
                                (when (nil? v)
                                  (let [default-fn (get prop-spec :default)]
                                    (if (fn? default-fn)
                                      (default-fn doc)
                                      default-fn))))))

(defn add-derived-values
  "Add values derived from other doc values."
  [is-update?]
  (fn [{:keys [properties]} doc]
    (update-leafs-via-directive properties
                                (walk-schema-with-stop :derived)
                                doc
                                (fn [_ prop-spec _v doc]
                                  (when-let [derive-fn (get prop-spec :derived)]
                                    (derive-fn doc is-update?))))))

(defn collect-computed
  "Collect computed values, for example cached values."
  [{:keys [properties]} doc]
  (update-leafs-via-directive properties
                              (walk-schema-with-stop :computed)
                              doc
                              (fn [_ prop-spec _v doc]
                                (when-let [compute-fn (get prop-spec :computed)]
                                  (compute-fn doc)))))

(defn cleanup-internal
  "Remove internal attributes"
  [_dtc doc]
  (dissoc doc :valid-to :pid :created-ts))

(defn- handle-ref-resources [properties intention doc]
  (r/reduce (fn [acc k v]
              (let [ref? (get-in properties [k :ref-resource])
                    keep? (case intention
                            :remove (not ref?)
                            :filter ref?)]
                (if keep?
                  (assoc acc k v)
                  acc)))
            {}
            doc))

(defn- add-ref-property-value [properties added-doc ref-docs]
  (map (fn [[property-name m]]
         (let [prefix  (-> properties
                           (get-in [property-name :ref-property-prefix]))
               name-id (-> prefix (str "-id") keyword)
               ;vid     (-> prefix (str "-vid") keyword)
               ]
           [property-name
            (map #(assoc %
                         :company-id (get added-doc :company-id)
                         name-id (get added-doc :id)
                    ;vid     (get added-doc :vid)
                         )
                 m)]))
       ref-docs))

(defn- insert-ref-docs!* [host-doc properties ref-prop]
  (let [[prop-name docs] ref-prop
        {:keys [ref-resource ref-resource-error pre-process]} (get properties prop-name)]
    (try
      @(p/conj!
        ref-resource
        (map (partial pre-process host-doc) docs))
      (catch Exception e
        (ref-resource-error e host-doc)))))

(defn- insert-ref-docs! [properties doc added-doc]
  (let [ref-props  (->> (handle-ref-resources properties :filter doc)
                        (add-ref-property-value properties added-doc))]
    (doseq [ref-prop ref-props]
      (insert-ref-docs!* added-doc properties ref-prop))))

(def execute-insert! ^{:skip-wrapper true}
  (fn [{:keys [collection properties]} doc]
    (let [docs       (if (map? doc) [doc] doc)
          core-docs  (r/map (partial handle-ref-resources properties :remove) docs)
          added-docs @(-> (m/conj! collection core-docs))]
      (doall (map (partial insert-ref-docs! properties) docs added-docs))
      added-docs)))

(defn execute-update! [{:keys [collection]} doc]
  ;;@(get collection :old-docs)
  @(m/update-in! collection {} doc))

(defn execute-destroy! [{:keys [collection]} _]
  @(m/disj! collection {}))

(defn execute-fetch [{:keys [collection] :as _dtc} _]
  @(m/select collection {}))

(def execute-count ^{:skip-wrapper true}
  (fn [{:keys [collection] :as _dtc} _]
    @(m/select-count collection {})))

                                        ; AssemblyLines

(defn wrapper-f [f dom-type-collection docs]
  (when-let [v (if (sequential? docs)
                 (doall (map (partial f dom-type-collection) docs))
                 (f dom-type-collection docs))]
    (with-meta v (meta docs))))

(def deref-steps [collect-computed cleanup-internal])

(def destroy! (partial assembly-line
                       [:execute execute-destroy!
                        :deref   []]
                       :environment))

(def insert! (partial assembly-line
                      [:validate     [validate-doc-input!
                                      (partial validate-properties! false)
                                      validate-properties2!
                                      validate-doc!
                                      validate-id-availability]
                       :mask         (mask/build-station :create)
                       :pre-process  [add-default-values (add-derived-values false)]
                       :execute      execute-insert!
                       :deref        deref-steps]
                      :wrapper-f wrapper-f
                      :environment))

(def update! (partial assembly-line
                      [:validate     [validate-doc-input!
                                      (partial validate-properties! true)
                                      validate-properties2!
                                      validate-doc!]
                       :mask         (mask/build-station :update)
                       :pre-process  (add-derived-values true)
                       :execute      execute-update!
                       :deref        deref-steps]
                      :wrapper-f wrapper-f
                      :environment))

(def fetch (partial assembly-line
                    [:execute execute-fetch
                     :mask    (mask/build-station :read)
                     :deref   deref-steps]
                    :wrapper-f wrapper-f
                    :environment))

(def fetch-count (partial assembly-line
                          [:execute execute-count
                           :deref []]
                          :environment))


                                        ; Constructor


(defn dom-type-collection [& {:as options}]
  {:pre [(get options :properties)
         (get options :name)]}

  (map->DomainTypeCollection
   (merge {:collection    (m/mongo-collection (collection-name options) (:skip-logging options))
           :update!-line  update!
           :insert!-line  insert!
           :destroy!-line destroy!
           :fetch-line    fetch
           :count-line    fetch-count}
          (-> options
              (update-in [:properties] merge s/default-properties)))))


                                        ; HTTP routes


(defn- add-stations* [line stations]
  (if (sequential? stations)
    (let [add-s (->> stations
                     (partition 3)
                     (map (fn [st]
                            (fn [line]
                              (apply line/add-stations line st))))
                     (apply comp))]
      (add-s line))
    line))

(defn pack-station
  "Add pack station to assembly line"
  [l]
  (line/add-stations l :first [:pack [validate-doc-input!, pack-doc]]))

(defn authorize-station [l f]
  (line/add-stations l :first [:authorize f]))

(defn authorize-read-station [l f]
  (line/add-stations l :after :deref [:authorize f]))

(defn dom-http-headers [restriction last-modified]
  (when last-modified
    (merge
     {"Last-Modified" (date/to-rfc-1123 last-modified)}
     (if (= #{:vid :id :company-id} (set (keys restriction)))
       {"Cache-Control" "private"
        "Expires" (date/to-rfc-1123 (.plus last-modified 1209600000))}
       {"Cache-Control" "max-age=1,must-revalidate,private"
        "Expires" nil}))))

(defn last-modified [doc collects]
  (when (and (empty? collects) (map? doc))
    (-> (DateTime. (:valid-from doc))
        (.withMillisOfSecond 0)
        (.plusSeconds 1))))

(defn dom-response [doc req _restriction _collects]
  (etag-response req doc))

(defn get-dtc-name [dtc]
  (or (:ability-name dtc) (:name dtc)))

(defn rest-routes [dom-type-collection & {:keys [index post put get delete path pre-process-dtc]
                                          :or {index  true
                                               post   true
                                               put    true
                                               get    true
                                               delete true}}]
  (let [path                (or path (str "/" (name (:name dom-type-collection))))
        list-truthy         (comp (partial filter identity) flatten list)]
    (apply routes
           (list-truthy

            (when index
              (GET path _req
                (fn [req]
                  (binding [context/*request* req]
                    (let [dom-type-collection (if pre-process-dtc
                                                (pre-process-dtc :index dom-type-collection req)
                                                dom-type-collection)
                          restriction         (pack/pack-query dom-type-collection req)
                          collects            (pack/pack-collects dom-type-collection req)]
                      (-> (p/select dom-type-collection restriction collects)
                          (authorize-station (fn [dtc doc]
                                               (ability/authorize!
                                                :index
                                                (get-dtc-name dom-type-collection)
                                                (get-in dtc [:collection :restriction]))
                                               doc))
                          (add-stations* index)
                          deref
                          (dom-response req restriction collects)))))))

            (when post
              (POST path _req
                (fn [req]
                  (binding [context/*request* req]
                    (let [dom-type-collection (if pre-process-dtc
                                                (pre-process-dtc :post dom-type-collection req)
                                                dom-type-collection)
                          document            (pack/pack-insert dom-type-collection req)]
                      (-> (p/conj! dom-type-collection document)
                          pack-station
                          (authorize-station (fn [_ doc]
                                               (ability/authorize! :create
                                                                   (get-dtc-name dom-type-collection)
                                                                   doc)
                                               doc))
                          (add-stations* post)
                          deref
                          first
                          json-response))))))

            (when post
              (POST (str "/bulk" path) _req
                (fn [req]
                  (binding [context/*request* req]
                    (let [dom-type-collection (if pre-process-dtc
                                                (pre-process-dtc :post dom-type-collection req)
                                                dom-type-collection)
                          documents            (pack/pack-bulk-insert dom-type-collection req)]
                      (-> (p/conj! dom-type-collection documents)
                          pack-station
                          (authorize-station (fn [_ doc]
                                               (ability/authorize! :create
                                                                   (get-dtc-name dom-type-collection)
                                                                   doc)
                                               doc))
                          (add-stations* post)
                          deref
                          json-response))))))

            (when put
              (let [p (str path "/:id")
                    f (fn [req]
                        (binding [context/*request* req]
                          (let [dom-type-collection (if pre-process-dtc
                                                      (pre-process-dtc :put dom-type-collection req)
                                                      dom-type-collection)
                                restriction         (pack/pack-query dom-type-collection req)
                                document            (pack/pack-update dom-type-collection req)]
                            (-> (p/update-in! dom-type-collection restriction document)
                                pack-station
                                (authorize-station (fn [_ doc]
                                                     (ability/authorize! :update
                                                                         (get-dtc-name dom-type-collection)
                                                                         doc)
                                                     doc))
                                (add-stations* put)
                                deref
                                first
                                json-response))))]
                [(PUT p _req f)
                 (PATCH p _req f)]))

            (when put
              (PUT (str "/archive" path) _req
                (fn [req]
                  (binding [context/*request* req]
                    (let [dom-type-collection (if pre-process-dtc
                                                (pre-process-dtc :put dom-type-collection req)
                                                dom-type-collection)
                          restriction         (pack/pack-post-query dom-type-collection req)]
                      (-> (p/update-in! dom-type-collection
                                        restriction
                                        {:archived (System/currentTimeMillis)})
                          (authorize-station (fn [_dtc doc]
                                               (ability/authorize! :update
                                                                   (get-dtc-name dom-type-collection)
                                                                   restriction ; or restriction?
                                                                   )
                                               doc))
                          (add-stations* put)
                          deref
                          json-response))))))
            (when put
              (PUT (str "/restore" path) _req
                (fn [req]
                  (binding [context/*request* req]
                    (let [dom-type-collection (if pre-process-dtc
                                                (pre-process-dtc :put dom-type-collection req)
                                                dom-type-collection)
                          restriction         (pack/pack-post-query dom-type-collection req)]
                      (-> (p/update-in! dom-type-collection
                                        restriction
                                        {:archived nil})
                          (authorize-station (fn [_dtc doc]
                                               (ability/authorize! :update
                                                                   (get-dtc-name dom-type-collection)
                                                                   restriction ; or restriction?
                                                                   )
                                               doc))
                          (add-stations* put)
                          deref
                          json-response))))))
            (when get
              (GET (str path "/:id") _req
                (fn [req]
                  (binding [context/*request* req]
                    (let [dom-type-collection (if pre-process-dtc
                                                (pre-process-dtc :get dom-type-collection req)
                                                dom-type-collection)
                          restriction         (pack/pack-query dom-type-collection req)
                          collects            (pack/pack-collects dom-type-collection req)]
                      (-> (p/select dom-type-collection restriction collects)
                          (authorize-read-station
                           (fn [_dtc doc]
                             (ability/authorize! :read
                                                 (get-dtc-name dom-type-collection)
                                                 doc)
                             doc))
                          (add-stations* get)
                          deref
                          first
                          (dom-response req restriction collects)))))))

            (when (and get (not (:skip-logging dom-type-collection)))
              (GET (str path "/:id/log") _req
                (fn [req]
                  (binding [context/*request* req]
                    (let [dom-type-collection (if pre-process-dtc
                                                (pre-process-dtc :get dom-type-collection req)
                                                dom-type-collection)
                          restriction         (pack/pack-query dom-type-collection req)]
                      (->> (m/fetch-log (collection-name dom-type-collection) restriction)
                           (ability/authorize-all! :read (get-dtc-name dom-type-collection))
                           json-response))))))

            (when delete
              (DELETE (str path "/:id") _req
                (fn [req]
                  (binding [context/*request* req]
                    (let [dom-type-collection (if pre-process-dtc
                                                (pre-process-dtc :delete dom-type-collection req)
                                                dom-type-collection)
                          restriction         (pack/pack-query dom-type-collection req)]
                      (-> (p/disj! dom-type-collection restriction)
                          (authorize-station (fn [_dtc doc]
                                               (ability/authorize! :delete
                                                                   (get-dtc-name dom-type-collection)
                                                                   (:params context/*request*) ; or restriction?
                                                                   )
                                               doc))
                          (add-stations* delete)
                          deref
                          json-response))))))

            (when delete
              (DELETE (str "/bulk" path) _req
                (fn [req]
                  (binding [context/*request* req]
                    (let [dom-type-collection (if pre-process-dtc
                                                (pre-process-dtc :delete dom-type-collection req)
                                                dom-type-collection)
                          restriction         (pack/pack-post-query dom-type-collection req)]
                      (-> (p/disj! dom-type-collection restriction)
                          (authorize-station
                           (fn [_dtc doc]
                             (ability/authorize-all! :delete
                                                     (get-dtc-name dom-type-collection)
                                                     @(p/select dom-type-collection restriction))
                             doc))
                          (add-stations* delete)
                          deref
                          json-response))))))))))

