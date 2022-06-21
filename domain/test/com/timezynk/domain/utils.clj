(ns com.timezynk.domain.utils
  (:require [spy.core :refer [stub]]
            [com.timezynk.domain.mongo.core :as m]))

(defn build-immutable-inmemory-store
  "Builds a fixture which overrides mongo.core functions so that the end result
   is an immutable store. The store resides wholly in memory."
  [records]
  (fn [f]
    (with-redefs [m/fetch         (stub records)
                  m/insert!       (stub records)
                  m/update-fast!  (stub records)
                  m/update!       (stub records)
                  m/destroy-fast! (stub {})
                  m/destroy!      (stub {})]
      (f))))
