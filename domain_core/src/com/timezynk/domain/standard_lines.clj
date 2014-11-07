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

(defn pack-station
  "Add pack station to assembly line"
  [aline]
  (line/add-stations aline :before :validate [:pack [validate-doc-input!, pack-doc]]))

; Apply Updaters

(declare map-leaf-walk)

(defn- update-array [v-org v-upd fun]
  (if (map? v-upd)
    (map #(map-leaf-walk %1 %2 fun)
         v-org
         v-upd)
    (fun v-org v-upd)))

(defn- fill-with-nil [m m2]
  (r/reduce (fn [m key]
              (update-in m [key] identity))
            m
            (keys m2)))

(defn- map-leaf-walk [m-org m-upd fun]
  (into {} (for [[k-upd v-upd] m-upd
                 [k-org v-org] (fill-with-nil m-org m-upd)
                 :when (= k-upd k-org)
                 :let  [new-v (cond
                               (map? v-upd)        (map-leaf-walk v-org v-upd fun)
                               (sequential? v-upd) (update-array v-org v-upd fun)
                               :else               (fun v-org v-upd))]]
             [k-upd new-v])))

(defn apply-updaters [{:keys [collection old-docs]} new-doc]
  (let [old-doc (-> old-docs deref first)]
    (map-leaf-walk old-doc
                   new-doc
                   (fn [old-v upd-v]
                     (if (fn? upd-v)
                       (upd-v old-v)
                       upd-v)))))



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

(defn add-default-values [{:keys [properties]} doc]
  "Like add-derived-values, but only when the doc is created"
  (update-leafs-via-directive properties
                              walk-schema
                              doc
                              (fn [_ prop-spec v doc]
                                (when-let [default-fn (get prop-spec :default)]
                                  (if (nil? v)
                                    (default-fn doc))))))

(defn add-derived-values [is-update?]
  "Add values derived from other doc values."
  (fn [{:keys [properties]} doc]
    (update-leafs-via-directive properties
                                walk-schema
                                doc
                                (fn [_ prop-spec v doc]
                                  (when-let [derive-fn (get prop-spec :derived)]
                                    (derive-fn doc is-update?))))))

(defn collect-computed
  "Collect computed values, for example cached values."
  [{:keys [properties]} doc]
  (update-leafs-via-directive properties
                              walk-schema
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
  (let [[prop-name docs] ref-prop]
    (let [{:keys [ref-resource pre-process]} (get properties prop-name)
          docs                               (map (partial pre-process host-doc) docs)]
      (-> ref-resource
          (p/conj! docs)
          (line/execute! :deref)
          println ;; this one is needed! Why?!?!
          ))))

(defn- insert-ref-docs! [properties doc added-doc]
  (let [ref-props  (->> (handle-ref-resources properties :filter doc)
                        (add-ref-property-value properties added-doc))]
    (doseq [ref-prop ref-props]
      (insert-ref-docs!* added-doc properties ref-prop))))

(defn execute-insert! [{:keys [collection properties]} doc]
  (let [core-doc   (handle-ref-resources properties :remove doc)
        added-docs @(rel/conj! collection core-doc)
        ]
    (doseq [added-doc added-docs]
      (insert-ref-docs! properties doc added-doc))
    added-docs))

(defn execute-update! [{:keys [collection restriction]} doc]
  @(rel/update-in! collection restriction doc))

(defn execute-destroy! [{:keys [collection restriction]} _]
  @(rel/disj! collection restriction))

(defn execute-fetch [{:keys [collection restriction] :as dtc} _]
  @(rel/select collection restriction))


                                        ; AssemblyLines

(defn- wrapper-f [f dom-type-collection docs]
  (if (sequential? docs)
    (map (partial f dom-type-collection) docs)
    (f dom-type-collection docs)))

(def deref-steps [collect-computed,
                  ;collect-referred
                  ])

(def destroy! (partial assembly-line
                       [:execute execute-destroy!
                        :deref   deref-steps]
                       :environment))

(def insert! (partial assembly-line
                      [:validate     [validate-doc-input!, (partial validate-properties! false), validate-doc!]
                       :pre-process  [add-default-values (add-derived-values false)]
                       :execute      [execute-insert!]
                       :deref        deref-steps]
                      :wrapper-f wrapper-f
                      :environment))

(def update! (partial assembly-line
                      [:clean        [remove-derived-values, remove-collected-values]
                       :updaters     apply-updaters
                       :validate     [validate-doc-input!, (partial validate-properties! true), validate-doc!]
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
