(ns com.timezynk.domain.http
  (:require [clojure.core.reducers :as r]
            [com.timezynk.domain.standard-lines :as lines]
            [com.timezynk.domain.assembly-line :as line]))

                                        ; TODO

;; (defn- add-stations* [line stations]
;;   (if (sequential? stations)
;;     (let [add-s (->> stations
;;                  (partition 3)
;;                  (map (fn [st]
;;                         (fn [line]
;;                           (apply line/add-stations line st))))
;;                  (apply comp))
;;           ]
;;       (add-s line))
;;     line))

;;                                         ; Assembly line steps in the HTTP layer

;; (defn pack-station
;;   "Add pack station to assembly line"
;;   [l]
;;   (line/add-stations l :first [:pack [lines/validate-doc-input!, lines/pack-doc]]))

;; (defn authorize-station [l f]
;;   (line/add-stations l :first [:authorize f]))

;; (defn dom-http-headers [restriction last-modified]
;;   (when last-modified
;;     (merge
;;       {"Last-Modified" (ud/to-rfc-1123 last-modified)}
;;       (if (= #{:vid :id :company-id} (set (keys restriction)))
;;         {"Cache-Control" "private"
;;          "Expires" (ud/to-rfc-1123 (.plus last-modified 1209600000))}
;;         {"Cache-Control" "max-age=1,must-revalidate,private"
;;          "Expires" nil}))))

;; (defn last-modified [doc collects]
;;   (when (and (empty? collects) (map? doc))
;;     (-> (DateTime. (:valid-from doc))
;;         (.withMillisOfSecond 0)
;;         (.plusSeconds 1))))

;; (defn dom-response [doc req restriction collects]
;;   (let [if-modified   (ud/parse-rfc-1123 (get-in req [:headers "if-modified-since"]))
;;         last-modified (last-modified doc collects)
;;         headers (dom-http-headers restriction last-modified)]
;;     (if (and last-modified if-modified (>= 0 (compare last-modified if-modified)))
;;       (json-response "" :status 304 :headers headers)
;;       (json-response doc :headers headers))))

;; (defn undelete [dtf vid]
;;   (congomongo/fetch-and-modify (collection-name dtf)
;;                                {:_id (um/object-id vid)}
;;                                {:$set {:valid-to nil}}
;;                                :return-new? true))

;; (def ^:dynamic *request* nil)


;;                    ; todo dom-type-collection -> dom-type-factory
;; (defn rest-routes [dom-type-collection & {:keys [index post put get delete path pre-process-dtc]
;;                                           :or {index  true
;;                                                post   true
;;                                                put    true
;;                                                get    true
;;                                                delete true}}]

;;   (let [path                (or path (str "/" (name (:name dom-type-collection))))
;;         list-truthy         (comp (partial filter identity) flatten list)]
;;     (apply routes
;;            (list-truthy

;;             (when index
;;               (GET path req
;;                    (fn [req]
;;                      (binding [*request* req]
;;                        (let [dom-type-collection (if pre-process-dtc
;;                                                    (pre-process-dtc :index dom-type-collection req)
;;                                                    dom-type-collection)
;;                              restriction         (pack/pack-query dom-type-collection req)
;;                              collects            (pack/pack-collects dom-type-collection req)
;;                              ]
;;                          (-> (p/select dom-type-collection restriction collects)
;;                              (authorize-station (fn [dtc doc]
;;                                                   ;; todo: handle default authorization
;;                                                   doc))
;;                              (add-stations* index)
;;                              deref
;;                              (dom-response req restriction collects)))))))

;;             (when post
;;               (POST path req
;;                     (fn [req]
;;                       (binding [*request* req]
;;                           (let [dom-type-collection (if pre-process-dtc
;;                                                       (pre-process-dtc :post dom-type-collection req)
;;                                                       dom-type-collection)
;;                                 document            (pack/pack-insert dom-type-collection req)]
;;                             (-> (p/conj! dom-type-collection document)
;;                                 pack-station
;;                                 (authorize-station (fn [_ doc]
;;                                                      ;; todo: handle default authorization
;;                                                      doc))
;;                                 (add-stations* post)
;;                                 deref
;;                                 first
;;                                 json-response))))))

;;             (when post
;;               (POST (str "/bulk" path) req
;;                     (fn [req]
;;                       (binding [*request* req]
;;                           (let [dom-type-collection (if pre-process-dtc
;;                                                       (pre-process-dtc :post dom-type-collection req)
;;                                                       dom-type-collection)
;;                                 documents            (pack/pack-bulk-insert dom-type-collection req)]
;;                             (-> (p/conj! dom-type-collection documents)
;;                                 pack-station
;;                                 (authorize-station (fn [_ doc]
;;                                                      ;; todo: handle default authorization
;;                                                      doc))
;;                                 (add-stations* post)
;;                                 deref
;;                                 json-response))))))

;;             (when put
;;               (let [p (str path "/:id")
;;                     f (fn [req]
;;                         (binding [*request* req]
;;                           (let [dom-type-collection (if pre-process-dtc
;;                                                       (pre-process-dtc :put dom-type-collection req)
;;                                                       dom-type-collection)
;;                                 restriction         (pack/pack-query dom-type-collection req)
;;                                 collects            (pack/pack-collects dom-type-collection req)
;;                                 document            (pack/pack-update dom-type-collection req)
;;                                 ]
;;                             (-> (p/update-in! dom-type-collection restriction document)
;;                                 pack-station
;;                                 (authorize-station (fn [_ doc]
;;                                                      ;; todo: handle default authorization
;;                                                      doc))
;;                                 (add-stations* put)
;;                                 deref
;;                                 first
;;                                 json-response))))]
;;                 [(PUT p req f)
;;                  (PATCH p req f)]))

;;             (when get
;;               (GET (str path "/:id") req
;;                    (fn [req]
;;                      (binding [*request* req]
;;                        (let [dom-type-collection (if pre-process-dtc
;;                                                    (pre-process-dtc :get dom-type-collection req)
;;                                                    dom-type-collection)
;;                              restriction         (pack/pack-query dom-type-collection req)
;;                              collects            (pack/pack-collects dom-type-collection req)
;;                              ]
;;                          (-> (p/select dom-type-collection restriction collects)
;;                              (authorize-station (fn [dtc doc]
;;                                                   ;; todo: handle default authorization
;;                                                   doc))
;;                              (add-stations* get)
;;                              deref
;;                              first
;;                              (dom-response req restriction collects)))))))

;;             (when delete
;;               (DELETE (str path "/:id") req
;;                       (fn [req]
;;                         (binding [*request* req]
;;                           (let [dom-type-collection (if pre-process-dtc
;;                                                       (pre-process-dtc :delete dom-type-collection req)
;;                                                       dom-type-collection)
;;                                 restriction         (pack/pack-query dom-type-collection req)]
;;                             (-> (p/disj! dom-type-collection restriction)
;;                                 (authorize-station (fn [dtc doc]
;;                                                      ;; todo: handle default authorization
;;                                                      doc))
;;                                 (add-stations* delete)
;;                                 deref
;;                                 json-response))))))

;;             (when delete
;;               (DELETE (str "/bulk" path) req
;;                       (fn [req]
;;                         (binding [*request* req]
;;                           (let [dom-type-collection (if pre-process-dtc
;;                                                       (pre-process-dtc :delete dom-type-collection req)
;;                                                       dom-type-collection)
;;                                 restriction         (pack/pack-post-query dom-type-collection req)]
;;                             (-> (p/disj! dom-type-collection restriction)
;;                                 (authorize-station (fn [dtc doc]
;;                                                      ;; todo: handle default authorization
;;                                                      doc))
;;                                 (add-stations* delete)
;;                                 deref
;;                                 json-response))))))

;;             (POST (str path "/undelete/:vid") req
;;                   (fn [req]
;;                     (ability/authorize! :delete (:name dom-type-collection) (:params req))
;;                     (let [restriction (pack/pack-query dom-type-collection req)]
;;                       (-> (undelete dom-type-collection (get-in req [:params :vid]))
;;                           json-response))))

;;             (GET (str "/schema" path ) req :todo!)))))
