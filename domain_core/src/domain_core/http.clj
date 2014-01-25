(ns tzbackend.future.domain.http
  (:require [clojure.tools.logging :as log :refer [spy warn info error]]
            [clojure.core.reducers :as r]))


(defn- add-stations* [line stations]
  (if (sequential? stations)
    (let [add-s (->> stations
                 (partition 3)
                 (map (fn [st]
                        (fn [line]
                          (apply line/add-stations line st))))
                 (apply comp))
          ]
      (add-s line))
    line))

(defn rest-routes [dom-type-collection & {:keys [index post put get delete path]
                                          :or {index  true
                                               post   true
                                               put    true
                                               get    true
                                               delete true}}]

  (let [path        (or path (str "/" (name (:name dom-type-collection))))
        list-truthy (comp (partial filter identity) flatten list)]
    (apply routes
           (list-truthy

            (when index
              (GET path req
                   (fn [req]
                     (ability/authorize! :index (:name dom-type-collection) (:params req))
                     (let [[restriction, collects] (pack/pack-query-and-collects dom-type-collection req)]
                       (-> (p/select dom-type-collection restriction collects)
                           (add-stations* index)
                           deref
                           json-response)))))

            (when post
              (POST path req
                    (fn [req]
                      (let [document (pack/pack-insert dom-type-collection req)]
                        (ability/authorize! :create (:name dom-type-collection) document)
                        (-> (p/conj! dom-type-collection document)
                            pack-station
                            (add-stations* post)
                            deref
                            first
                            json-response)))))

            (when put
              (let [p (str path "/:id")
                    f (fn [req]
                        (let [[restriction, collects] (pack/pack-query-and-collects dom-type-collection req)
                              document                (pack/pack-update dom-type-collection req)]
                          (ability/authorize! :update (:name dom-type-collection) document)
                          (-> (p/update-in! dom-type-collection restriction document)
                              pack-station
                              (add-stations* put)
                              deref
                              first
                              json-response)))]
                [(PUT p req f)
                 (PATCH p req f)]))

            (when get
              (GET (str path "/:id") req
                   (fn [req]
                     (ability/authorize! :read (:name dom-type-collection) (:params req))
                     (let [[restriction, collects] (pack/pack-query-and-collects dom-type-collection req)]
                       (-> (p/select dom-type-collection restriction collects)
                           (add-stations* get)
                           deref
                           first
                           json-response)))))

            (when delete
              (DELETE (str path "/:id") req
                      (fn [req]
                        (ability/authorize! :delete (:name dom-type-collection) (:params req))
                        (let [[restriction _] (pack/pack-query-and-collects dom-type-collection req)]
                          (-> (p/disj! dom-type-collection restriction)
                              (add-stations* delete)
                              deref
                              json-response)))))

            (GET (str path "/schema") req :todo!)))))
