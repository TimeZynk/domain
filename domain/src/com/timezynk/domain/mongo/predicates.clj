(ns com.timezynk.domain.mongo.predicates
  "Predicates used in a query. They will be translated into a mongo query.")

(defn- field-pos [x y]
  (let [x? (keyword? x)
        y? (keyword? y)]
    (when (and x? y?)
      (throw (Exception. (str "Both arguments '" x "' and '" y "' are keywords."))))
    (when (and (not x?) (not y?))
      (throw (Exception. (str "No argument, of '" x "' and '" y "' is a keyword."))))
    (if x? 0 1)))

(defn in [field & values]
  {field {:$in (or (seq values) [])}})

(defn exists [field]
  {field {:$exists true}})

(defn <* [x y]
  (let [[operator field value] (if (= 0 (field-pos x y))
                                 [:$lt x y]
                                 [:$gt x y])]
    {field {operator value}}))

(defn >* [x y]
  (let [[operator field value] (if (= 0 (field-pos x y))
                                 [:$gt x y]
                                 [:$lt x y])]
    {field {operator value}}))

(defn <=* [x y]
  (let [[operator field value] (if (= 0 (field-pos x y))
                                 [:$lte x y]
                                 [:$gte x y])]
    {field {operator value}}))

(defn >=* [x y]
  (let [[operator field value] (if (= 0 (field-pos x y))
                                 [:$gte x y]
                                 [:$lte x y])]
    {field {operator value}}))

(defn =* [x y]
  (let [[field value] (if (= 0 (field-pos x y))
                        [x y]
                        [y x])]
    {field value}))

(defn !=* [x y]
  (let [[field value] (if (= 0 (field-pos x y))
                        [x y]
                        [y x])]
    {field {:$ne value}}))

(defn not* [pred]
  {:$not pred})

(defn and* [& args]
  {:$and args})

(defn or* [& args]
  {:$or args})
