(ns com.timezynk.domain.middleware
  (:require
   [clojure.core.reducers :as r]
   [cheshire.core :refer [parse-string]]
   clojure.string))

(defn- v-vector [v]
  (clojure.string/split v #"\;"))

(defn- set-value [v]
  (when-not (or (= "null" v)
                (= "nil" v))
    v))

(defn- collect-param-value [v]
  {:fields (if (= "true" v)
             :all
             (some->> v v-vector (map keyword) (into [])))})

(defn- parse-query-param [[k v]]
  (let [[_ n op]  (or (re-find #"^([a-z-_A-Z]+)\[([a-z]+)\]" k)
                      (re-find #"^([a-zA-Z0-9\-_.]+)" k))
        prop-name (keyword n)
        op        (when-not (nil? op)
                    (keyword (str "_" op "_")))
        v         (set-value v)]
    (cond
      (= :_collect_ op) [:_collect_ {prop-name (collect-param-value v)}]
      (= :_in_ op)      [prop-name, {op (when-not (nil? v) (v-vector v))}]
      op                [prop-name, {op v}]
      :else             [prop-name, v])))

(defn- q-param [q]
  (try
    (->> (parse-string q true)
         (into []))
    (catch Exception _e
      [])))

(defn- split-params [query]
  [(dissoc query :_collect_)
   (get query :_collect_)])

(defn- into-map-merge [v]
  (r/reduce (fn [acc [k v]]
              (update-in acc
                         [k]
                         #(if (map? v)
                            (merge % v)
                            v)))
            {}
            v))

(defn parse-params [request]
  (let [{:keys [query-params]} request]
    (->> (dissoc query-params "q")
         (map parse-query-param)
         (concat (q-param (get query-params "q" "")))
         into-map-merge
         split-params)))

(defn wrap-domain-query-and-collect-params [handler]
  (fn [request]
    (let [[query-params, collect-params] (parse-params request)]
      (handler (assoc request
                      :domain-query-params query-params
                      :domain-collect-params collect-params)))))
