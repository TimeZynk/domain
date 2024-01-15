(defproject com.timezynk/domain-types "1.0.6"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "BSD 3 Clause"
            :url "https://opensource.org/licenses/BSD-3-Clause"}
  :dependencies [[com.timezynk/domain "2.1.0"]
                 [com.timezynk/useful "2.3.0" :scope "dev"]
                 [congomongo "2.6.0" :scope "provided"]
                 [org.clojure/clojure "1.11.1" :scope "provided"]
                 [slingshot "0.12.2"]]
  :repl-options {:init-ns domain-types.core}
  :plugins [[lein-cljfmt "0.6.7"]])
