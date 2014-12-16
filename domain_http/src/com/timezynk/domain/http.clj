(ns com.timezynk.domain.http
  (:require [clojure.core.reducers              :as r]
            [com.timezynk.domain.standard-lines :as lines]
            [com.timezynk.domain.assembly-line  :as line]
            [compojure.core                     :refer [routes GET POST PUT PATCH DELETE]]
            [com.timezynk.domain.pack           :as pack]
            [com.timezynk.domain.date           :as date]
            [com.timezynk.domain.json           :refer [json-response]]
            [com.timezynk.domain                :as dom])
  (:import [org.joda.time DateTime]))

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

(defn pack-station
  "Add pack station to assembly line"
  [l]
  (line/add-stations l :first [:pack [lines/validate-doc-input!, lines/pack-doc]]))

(defn authorize-station [l f]
  (line/add-stations l :first [:authorize f]))


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

(defn dom-response [doc req restriction collects]
  (let [if-modified   (date/parse-rfc-1123 (get-in req [:headers "if-modified-since"]))
        last-modified (last-modified doc collects)
        headers (dom-http-headers restriction last-modified)]
    (if (and last-modified if-modified (>= 0 (compare last-modified if-modified)))
      (json-response "" :status 304 :headers headers)
      (json-response doc :headers headers))))

(def ^:dynamic *request* nil)

(defn rest-routes [dom-type-factory & {:keys [index post put get delete path pre-process-dtf]
                                       :or   {index  true
                                              post   true
                                              put    true
                                              get    true
                                              delete true}}]

  (let [path                (or path (str "/" (name (:name dom-type-factory))))
        list-truthy         (comp (partial filter identity) flatten list)]
    (apply routes
           (list-truthy

            (when index
              (GET path req
                   (fn [req]
                     (binding [*request* req]
                       (let [dom-type-factory (if pre-process-dtf
                                                (pre-process-dtf :index dom-type-factory req)
                                                dom-type-factory)
                             restriction      (pack/pack-query dom-type-factory req)
                             collects         (pack/pack-collects dom-type-factory req)
                             ]
                         (-> (dom/select dom-type-factory restriction collects)
                             (authorize-station (fn [dtc doc]
                                                  ;; todo: handle default authorization
                                                  doc))
                             (add-stations* index)
                             deref
                             (dom-response req restriction collects)))))))

            (when post
              (POST path req
                    (fn [req]
                      (binding [*request* req]
                          (let [dom-type-factory (if pre-process-dtf
                                                      (pre-process-dtf :post dom-type-factory req)
                                                      dom-type-factory)
                                document            (pack/pack-insert dom-type-factory req)]
                            (-> (dom/conj! dom-type-factory document)
                                pack-station
                                (authorize-station (fn [_ doc]
                                                     ;; todo: handle default authorization
                                                     doc))
                                (add-stations* post)
                                deref
                                ;first
                                json-response))))))

            (when post
              (POST (str "/bulk" path) req
                    (fn [req]
                      (binding [*request* req]
                          (let [dom-type-factory (if pre-process-dtf
                                                      (pre-process-dtf :post dom-type-factory req)
                                                      dom-type-factory)
                                documents            (pack/pack-bulk-insert dom-type-factory req)]
                            (-> (dom/conj! dom-type-factory documents)
                                pack-station
                                (authorize-station (fn [_ doc]
                                                     ;; todo: handle default authorization
                                                     doc))
                                (add-stations* post)
                                deref
                                json-response))))))

            (when put
              (let [p (str path "/:id")
                    f (fn [req]
                        (println "")
                        (println ">>>>>put<<<<<<")
                        (println req)
                        (println ">>>>>put<<<<<<")
                        (println "")
                        (binding [*request* req]
                          (let [dom-type-factory (if pre-process-dtf
                                                      (pre-process-dtf :put dom-type-factory req)
                                                      dom-type-factory)
                                restriction         (pack/pack-query dom-type-factory req)
                                collects            (pack/pack-collects dom-type-factory req)
                                document            (pack/pack-update dom-type-factory req)
                                ]
                            (-> (dom/update-in! dom-type-factory restriction document)
                                pack-station
                                (authorize-station (fn [_ doc]
                                                     ;; todo: handle default authorization
                                                     doc))
                                (add-stations* put)
                                deref
                                ;first
                                json-response))))]
                [(PUT p req f)
                 (PATCH p req f)]))

            (when get
              (GET (str path "/:id") req
                   (fn [req]
                     (binding [*request* req]
                       (let [dom-type-factory (if pre-process-dtf
                                                (pre-process-dtf :get dom-type-factory req)
                                                dom-type-factory)
                             restriction      (pack/pack-query dom-type-factory req)
                             collects         (pack/pack-collects dom-type-factory req)
                             ]
                         (-> (dom/select dom-type-factory restriction collects)
                             (authorize-station (fn [dtc doc]
                                                  ;; todo: handle default authorization
                                                  doc))
                             (add-stations* get)
                             deref
                             first
                             (dom-response req restriction collects)))))))

            (when delete
              (DELETE (str path "/:id") req
                      (fn [req]
                        (binding [*request* req]
                          (let [dom-type-factory (if pre-process-dtf
                                                      (pre-process-dtf :delete dom-type-factory req)
                                                      dom-type-factory)
                                restriction         (pack/pack-query dom-type-factory req)]
                            (-> (dom/disj! dom-type-factory restriction)
                                (authorize-station (fn [dtc doc]
                                                     ;; todo: handle default authorization
                                                     doc))
                                (add-stations* delete)
                                deref
                                json-response))))))

            (when delete
              (DELETE (str "/bulk" path) req
                      (fn [req]
                        (binding [*request* req]
                          (let [dom-type-factory (if pre-process-dtf
                                                      (pre-process-dtf :delete dom-type-factory req)
                                                      dom-type-factory)
                                restriction         (pack/pack-post-query dom-type-factory req)]
                            (-> (dom/disj! dom-type-factory restriction)
                                (authorize-station (fn [dtc doc]
                                                     ;; todo: handle default authorization
                                                     doc))
                                (add-stations* delete)
                                deref
                                json-response))))))

            ;; Todo: add the undelete functionality
            ;; (POST (str path "/undelete/:vid") req
            ;;       (fn [req]
            ;;         ;;(ability/authorize! :delete (:name dom-type-factory) (:params req))
            ;;         ;; todo: handle authorization
            ;;         (let [restriction (pack/pack-query dom-type-factory req)]
            ;;           (-> (undelete dom-type-factory (get-in req [:params :vid]))
            ;;               json-response))))

            (GET (str "/schema" path ) req :todo!)))))
