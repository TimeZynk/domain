(ns com.timezynk.domain.update-leafs
  (:require [clojure.core.reducers :as r]))


                                        ; Update leafs

(declare update-leafs*)

(defn- update-value [trail fun & args]
  (fn [m p]
    (update-in m [p]
               #(apply update-leafs* (conj trail p) % fun %2)
               args)))

(defn- update-hash-map [p fun m & args]
  (r/reduce (apply update-value p fun args)
            m
            (keys m)))

(defn- update-leafs* [p v fun & args]
  (cond
    (sequential? v) (map #(apply update-leafs* (conj p []) % fun args) v)
    (map? v)        (apply update-hash-map p fun v args)
    :else           (apply fun p v args)))

(defn update-leafs
  "Recursive update on leafs in sequences and maps"
  [x fun & args]
  (apply update-leafs* () x fun args))


                                        ; With Directives

(declare walk-directives)

(defn- walk-directive [dir-fun trail upd-fun args]
  (fn [x dir-k dir-v]
    (let [trail                (conj trail dir-k)
          [sub-trail, sub-dir] (dir-fun trail dir-v x)
          is-vector?           (= [] (first sub-trail))
          sub-trail            (if is-vector?
                                 (rest sub-trail)
                                 sub-trail)
          path                 (reverse sub-trail)
          parent-path          (if is-vector?
                                 path
                                 (-> sub-trail rest reverse))
          parent-exists?       (or false ;(= 1 (count path)) what does this do?
                                   (get-in x parent-path))
          x                    (if parent-exists?
                                 (let [old-v (get-in x path)
                                       new-v (apply upd-fun trail dir-v old-v x args)]
                                   (if-not (nil? new-v)
                                     (update-in x path (fn [_] new-v))
                                     x))
                                 x)
          x                    (if sub-dir
                                 (if (and is-vector? parent-exists?)
                                   (update-in x
                                              parent-path
                                              (fn [y]
                                                (map
                                                 #(walk-directives sub-dir
                                                                   dir-fun
                                                                   ()
                                                                   %
                                                                   upd-fun
                                                                   args)
                                                 y)))
                                   (walk-directives sub-dir dir-fun trail x upd-fun args))
                                 x)]
      x)))

(defn- walk-directives [dirs dir-fun p x upd-fun args]
  (r/reduce (walk-directive dir-fun p upd-fun args)
            x
            dirs))

(defn update-leafs-via-directive
  "Recursive update on entries in a map using a second map as a collection of directives"
  [dirs dir-fun x upd-fun & args]
  (walk-directives dirs dir-fun () x upd-fun args))
