(ns com.timezynk.assembly-line
  (:require [clojure.core.reducers     :as r]))

(defprotocol AssemblyLineExecute
  (add-stations [this placement new-stations]
                [this placement target-station new-stations]
    "Inject a step :before or :after another step, or :replace it.")
  (prepare [this arg]
    "Prepare a recipe for execution with provided value as the initial argument.")
  (execute! [this] [this pause-at]
    "Execute steps from current position until finished
     or if key is provided, until that step is reached. The flag async?
     switches between synchronous and asynchronous execution, and is
     true by default."))

(defn- todo [steps start-at end-at]
  (let [start-index (if start-at (.indexOf steps start-at) 0)
        end-index   (if end-at
                      (.indexOf steps end-at)
                      (count steps))
        ]
    (->> steps (take end-index) (drop start-index))))

(defn wrap [wrapper-f]
  (fn [station]
    (if (:skip-wrapper (meta station))
      station
      (partial wrapper-f station))))

(defrecord AssemblyLine [environment stations station-order at in-production wrapper-f execution-wrapper-f]

  AssemblyLineExecute
  (add-stations [this placement new-stations] (add-stations this placement nil new-stations))
  (add-stations [this placement target-station new-stations]
    (-> this

        (assoc
          :station-order
          (let [target-pos (.indexOf station-order target-station)
                _          (when (and (neg? target-pos)
                                      (contains? #{:after :before :replace} placement))
                             (throw (Exception. (str target-station " is not a valid station"))))
                [t d]      (case placement
                             :after   [(+ 1 target-pos) (+ 1 target-pos)]
                             :before  [target-pos target-pos]
                             :replace [target-pos (+ 1 target-pos)]
                             :first   [0 0]
                             :last    [(count station-order) (count station-order)]
                             (throw (Exception. (str placement " is not a valid placement"))))
                ]
            (->> (concat (take t station-order)
                         (->> new-stations (partition 2) (map first))
                         (drop d station-order))
                 (into [])))

          :stations
          (merge (apply hash-map new-stations)
                 stations))

        (update-in [:at] (fn [at]
                           (if (= ::finished at)
                             (first new-stations)
                             at)))))

  (execute! [this] (execute! this nil))

  (execute! [this pause-at]
    (let [todo-list     (todo station-order at pause-at)
          ;;todo rewrite with cond->>
          ordered-steps (->> todo-list
                             (r/map #(get stations % []))
                             (r/flatten))
          ordered-steps (if wrapper-f
                          (r/map (wrap wrapper-f) ordered-steps)
                          ordered-steps)
          ordered-steps (if environment
                          (r/map #(partial % environment) ordered-steps)
                          ordered-steps)
          to-run        (->> ordered-steps
                             (into [])
                             (reverse)
                             (apply comp))]
      (assoc this
        :in-production (to-run in-production)
        :at            (or pause-at ::finished))))

  (prepare [this arg]
    (assoc this :in-production arg))

  clojure.lang.IDeref
  (deref [this]
    (if (= ::finished at)
      in-production
      @(execute! this nil))))

(defmethod print-method AssemblyLine [recipe ^java.io.Writer w]
  (let [br  (fn [] (.write w "\n"))
        pln (fn [& args]
              (doseq [s args] (.write w (str s)))
              (br))
        {:keys [stations station-order at in-production environment]} recipe]
    (when environment
      (pln "ENVIRONMENT")
      (pln (str environment))
      (br))
    (pln "STATIONS")
    (doseq [step station-order]
      (pln (str (name step) ": ") (get stations step [])))
    (br)
    (pln "---------")
    (pln "Currently in-production: " in-production)
    (cond
      (= at ::finished) (pln "Finished")
      at                (pln "Currently at station " (str at))
      :else             (pln "Not started"))))

(prefer-method print-method java.util.Map clojure.lang.IDeref)

(defn assembly-line [steps & {:keys [environment wrapper-f execution-wrapper-f]}]
  (let [stations      (apply hash-map steps)
        station-order (->> steps (partition 2) (map first) (into []))]
    (map->AssemblyLine {:stations            stations
                        :station-order       station-order
                        :at                  nil
                        :in-production       nil ;(delay nil)
                        :environment         environment
                        :wrapper-f           wrapper-f
                        :execution-wrapper-f execution-wrapper-f})))
