(ns com.timezynk.domain-types.breaks
  (:require [com.timezynk.domain.schema :as s]
            [com.timezynk.domain.pack :as pack]
            [com.timezynk.domain.persistence :as p]
            [slingshot.slingshot :refer [throw+]]))

(defn break [& options]
  (apply s/map
         {:start (s/date-time)
          :end   (s/date-time)}
         options))

(defn breaks [& options]
  (apply s/vector (break) options))

(defn- use-or-load [dtc doc]
  (if (and (:start doc) (:end doc))
    doc
    (let [vid (or (:vid doc) (-> dtc :collection :restriction :vid))]
      (when vid
        (->> (p/by-vid dtc vid)
             (pack/pack-doc dtc))))))

(defn validate-breaks! [dtc doc]
  (let [{:keys [start end breaks]} (use-or-load dtc doc)]
    (if (seq breaks)
      (let [sorted-breaks (sort-by :start breaks)]
        (doseq [break sorted-breaks]
          (when (or (.isBefore (:start break) start)
                    (.isBefore end (:start break))
                    (.isBefore end (:end break))
                    (.isBefore (:end break) (:start break)))
            (throw+ {:type    :validation-error
                     :message "Break outside of boundaries"
                     :break   break})))
        (assoc doc :breaks sorted-breaks))
      doc)))

