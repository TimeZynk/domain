(defproject com.timezynk/domain-types "1.0.20"
  :description "Modeling extras built on top of domain"
  :url "https://github.com/TimeZynk/domain/tree/master/domain_types"
  :license {:name "BSD 3 Clause"
            :url "https://opensource.org/licenses/BSD-3-Clause"}
  :dependencies [[com.timezynk/domain "2.6.0" :scope "provided"]
                 [com.timezynk/useful "4.10.0" :scope "provided"]
                 [congomongo "2.6.0" :scope "provided"]
                 [org.clojure/clojure "1.11.1" :scope "provided"]
                 [slingshot "0.12.2"]]
  :repl-options {:init-ns domain-types.core}
  :plugins [[lein-cljfmt "0.6.7"]])
