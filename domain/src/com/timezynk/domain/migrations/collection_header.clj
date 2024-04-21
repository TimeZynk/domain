(ns com.timezynk.domain.migrations.collection-header
  "The collection header adds information about migrations"
  (:require [com.timezynk.domain.core :as dom]
          ; [com.timezynk.domain.schema :as s]
            [com.timezynk.mongo :as mongo2]))


#_(def collection-headers
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
        (assoc-in [:properties :id] (s/string))))

(defn by-collection [collection]
  (mongo2/fetch-by-id :collection.headers
                      (if (keyword? collection)
                        collection
                        (dom/collection-name collection))))

(defn migration-made? [collection migr-name]
  (let [collection-name (if (keyword? collection)
                          collection
                          (dom/collection-name collection))]
    (boolean
     (mongo2/fetch-one :collection.headers
                       {:_id collection-name
                        (str "migrations." (name migr-name)) {:$exists true}}))))

(defn create! [collection migration-name source-collection]
  (let [collection-name (if (keyword? collection)
                          collection
                          (dom/collection-name collection))
        header?         (mongo2/fetch-by-id :collection.headers
                                            collection-name)]
    (when-not header?
      (mongo2/insert! :collection.headers
                      {:_id           collection-name
                       migration-name {:source-collection source-collection}}))))

(defn update! [dom-type-collection new-doc]
  (let [collection-name (dom/collection-name dom-type-collection)]
    (mongo2/update-by-id! :collection.headers
                          collection-name
                          new-doc)))

(defn set-migration-not-made! [collection migr-name]
  (mongo2/set-by-id! :collection.headers
                     (if (keyword? collection)
                       collection
                       (dom/collection-name collection))
                     {(str "migrations." (name migr-name)) false}))

(defn migration-finished [collection migr-name]
  (let [collection-name (if (keyword? collection)
                          collection
                          (dom/collection-name collection))]
    (mongo2/set-by-id! :collection.headers
                       collection-name
                       {(str "migrations." (name migr-name)) (System/currentTimeMillis)})))
