(ns com.timezynk.domain.redaction-test
  "Elevation of authorization barrier on a per-property basis"
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
[clojure.tools.logging :as log]
            [spy.core :refer [stub spy called-once? call-matching?]]
            [com.timezynk.domain.core :refer [dom-type-collection]]
            [com.timezynk.domain.schema :as s]
            [com.timezynk.domain.mongo.core :as m]
            [com.timezynk.useful.cancan :as ability]
            [com.timezynk.domain.persistence :as p]))

(def dtc
  (dom-type-collection :name :qwerty
                       :properties {:x (s/string)
                                    :y (s/string :authorize-individually? true)
                                    :z (s/string)}))

(def ^:const records
  [{:x "abc" :y "123" :z "xyz"}])

(defn- with-immutable-inmemory-store [f]
  (with-redefs [m/fetch         (stub records)
                m/insert!       (stub {})
                m/update-fast!  (spy (fn [_ _ doc] doc))
                m/update!       (spy (fn [_ _ doc] doc))
                m/destroy-fast! (stub {})
                m/destroy!      (stub {})]
    (f)))

(use-fixtures :once #'with-immutable-inmemory-store)

(deftest reading
  (testing "Reading from the store"
    (with-redefs [ability/can? (stub false)]
      (let [result @(p/select dtc)
            doc (first result)]
        (testing "marked property"
          (is (not (contains? doc :y))))
        (testing "unmarked properties"
          (is (contains? doc :x))
          (is (contains? doc :z)))
        (testing "authorization"
          (testing "times called"
            (is (called-once? ability/can?)))
          (testing "action"
            (is (call-matching? ability/can? (comp #{:read-property-y} first))))
          (testing "object"
            (is (call-matching? ability/can? (comp #{:qwerty} second)))))))))
