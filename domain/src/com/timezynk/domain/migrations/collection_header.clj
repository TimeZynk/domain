(ns com.timezynk.domain.migrations.collection-header
  "The collection header adds information about migrations"
  (:require [com.timezynk.domain.core :as dom]
          ; [com.timezynk.domain.schema :as s]
            [somnium.congomongo :as mongo]))

(comment
  (def collection-headers
    (-> (dom/dom-type-collection
         :name :collection-headers
         :description "The collection header adds information about migrations"
         :properties {:migrations (s/any :default (fn [_] {}))
                      :info       (s/any :default (fn [_] {}))})
        (update-in [:properties]
                   dissoc
                   :created
                   :created-by
                   :changed-by
                   :company-id)
        (assoc-in [:properties :id] (s/string)))))

(defn by-collection [collection]
  (mongo/fetch-one :collection.headers
                   :where {:_id (if (keyword? collection)
                                  collection
                                  (dom/collection-name collection))}))

(defn migration-made? [collection migr-name]
  (let [collection-name (if (keyword? collection)
                          collection
                          (dom/collection-name collection))]
    (boolean
     (mongo/fetch-one :collection.headers
                      :where {:_id collection-name
                              (str "migrations." (name migr-name)) {:$exists true}}))))

(defn create! [collection migration-name source-collection]
  (let [collection-name (if (keyword? collection)
                          collection
                          (dom/collection-name collection))
        header?         (mongo/fetch-one :collection.headers
                                         :where {:_id collection-name})]
    (when-not header?
      (mongo/insert! :collection.headers
                     {:_id           collection-name
                      migration-name {:source-collection source-collection}}))))

(defn update! [dom-type-collection new-doc]
  (let [collection-name (dom/collection-name dom-type-collection)]
    (mongo/update! :collection.headers
                   {:_id collection-name}
                   new-doc)))

(defn set-migration-not-made! [collection migr-name]
  (mongo/update! :collection.headers
                 {:_id (if (keyword? collection)
                         collection
                         (dom/collection-name collection))}
                 {:$set {(str "migrations." (name migr-name)) false}}))

(defn migration-finished [collection migr-name]
  (let [collection-name (if (keyword? collection)
                          collection
                          (dom/collection-name collection))]
    (mongo/update! :collection.headers
                   {:_id collection-name}
                   {:$set {(str "migrations." (name migr-name)) (System/currentTimeMillis)}})))
