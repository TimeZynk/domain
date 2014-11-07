(ns com.timezynk.domain.standard-lines
  "Useful steps to add to assembly lines in tzbackend.future.domain"
  (:require [clojure.core.reducers              :as r]
            [com.timezynk.domain.validation             :as v]
            [com.timezynk.domain.pack                   :as pack]
            [com.timezynk.domain.assembly-line :as line]
            [com.timezynk.domain.persistence            :as p]
            [com.timezynk.domain.relation               :as rel]
            [com.timezynk.domain.update-leafs           :refer [update-leafs, update-leafs-via-directive]]
            [com.timezynk.domain.assembly-line :refer [assembly-line]]
            [clojure.pprint :refer [pprint]]))


                                        ; Steps

(defn validate-doc-input!
  "Validate the format of the json doc values, and assures they can be converted to the
   property type on the server side?"
  [{:keys [properties]} doc]
  (v/validate-json-input! properties doc)
  doc)

(defn pack-doc
  "Convert the document values, from json types to server types."
  [collection doc]
  (pack/pack-doc collection doc))

(defn remove-derived-values
  "Derived values should be created by the system"
  [{:keys [properties]} doc]
  ;(warn "remove-derived-values not implemented")
  doc)

(defn remove-collected-values [{:keys [properties]} doc]
  ;(warn "remove-collected-values! not implemented")
  doc)

(defn validate-doc! [{:keys [validate-doc]} doc]
  (when validate-doc
    (v/validate-doc! validate-doc doc))
  doc)

(defn validate-properties! [all-optional? {:keys [name properties]} doc]
  (v/validate-properties! all-optional? properties doc)
  doc)

(defn- walk-schema [trail prop-spec doc]
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
  [{:keys [name properties]} doc]
  (update-leafs-via-directive properties
                              (walk-schema-with-stop :validate)
                              doc
                              (fn [[prop-name] prop-spec v doc]
                                (when-let [validate-fn (get prop-spec :validate)]
                                  (when-let [errs (validate-fn v doc)]
                                    (throw+ {:type     :tzbackend.future.domain.validation/validation-error
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
                                (when-let [default-fn (get prop-spec :default)]
                                  (when (nil? v)
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
                                (fn [_ prop-spec v doc]
                                  (when-let [derive-fn (get prop-spec :derived)]
                                    (derive-fn doc is-update?))))))

(defn collect-computed
  "Collect computed values, for example cached values."
  [{:keys [properties]} doc]
  (update-leafs-via-directive properties
                              (walk-schema-with-stop :computed)
                              doc
                              (fn [_ prop-spec v doc]
                                (when-let [compute-fn (get prop-spec :computed)]
                                  (compute-fn doc)))))

(defn- handle-ref-resources [properties intention doc]
  (r/reduce (fn [doc k v]
              (let [ref? (get-in properties [k :ref-resource])
                    keep? (case intention
                            :remove (not ref?)
                            :filter ref?)]
                (if keep?
                  (assoc doc k v)
                  doc)))
            {}
            doc))

;; todo: remove?
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

;; todo: remove?
(defn- insert-ref-docs!* [host-doc properties ref-prop]
  (let [[prop-name docs] ref-prop]
    (let [{:keys [ref-resource pre-process]} (get properties prop-name)
          docs                               (map (partial pre-process host-doc) docs)]
      (-> ref-resource
          (p/conj! docs)
          (line/execute! :deref)
          println ;; this one is needed! Why?!?!
          ))))

;; todo: remove?
(defn- insert-ref-docs! [properties doc added-doc]
  (let [ref-props  (->> (handle-ref-resources properties :filter doc)
                        (add-ref-property-value properties added-doc))]
    (doseq [ref-prop ref-props]
      (insert-ref-docs!* added-doc properties ref-prop))))

(def execute-insert! ^{:skip-wrapper true}
  (fn [{:keys [collection properties]} doc]
    (let [docs       (if (map? doc) [doc] doc)
          core-docs  (r/map (partial handle-ref-resources properties :remove) docs)
          added-docs @(-> (m/conj! collection core-docs))
          ]
      (doall (map (partial insert-ref-docs! properties) docs added-docs))
      added-docs)))

(defn execute-update! [{:keys [collection restriction]} doc]
  @(rel/update-in! collection restriction doc))

(defn execute-destroy! [{:keys [collection restriction]} _]
  @(rel/disj! collection restriction))

(defn execute-fetch [{:keys [collection restriction] :as dtc} _]
  @(rel/select collection restriction))

;; todo: keep?
(defn unpack-destroy-result [{:keys [collection]} doc]
  ;(warn "unpack-destroy-result not implemented")
  doc)

                                        ; AssemblyLines

(defn wrapper-f [f dom-type-factory docs]
  (when-let [v (if (sequential? docs)
                 (doall (map (partial f dom-type-factory) docs))
                 (f dom-type-factory docs))]
    (with-meta v (meta docs))))

(def deref-steps [collect-computed])

(def destroy! (partial assembly-line
                       [:execute execute-destroy!
                        :deref   []]
                       :environment))

(def insert! (partial assembly-line
                      [:validate     [validate-doc-input!
                                      (partial validate-properties! false)
                                      validate-properties2!
                                      validate-doc!]
                       :pre-process  [add-default-values (add-derived-values false)]
                       :execute      execute-insert!
                       :deref        deref-steps]
                      :wrapper-f wrapper-f
                      :environment))

(def update! (partial assembly-line
                      [:clean        [remove-derived-values, remove-collected-values]
                       :validate     [validate-doc-input!
                                      (partial validate-properties! true)
                                      validate-properties2!
                                      validate-doc!]
                       :pre-process  (add-derived-values true)
                       :execute      execute-update!
                       :deref        deref-steps]
                      :wrapper-f wrapper-f
                      :environment))

(def fetch (partial assembly-line
                    [:execute      execute-fetch
                     :deref        deref-steps]
                    :wrapper-f wrapper-f
                    :environment))
