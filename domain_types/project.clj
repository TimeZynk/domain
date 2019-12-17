(defproject com.timezynk/domain-types "1.0.0"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "BSD 3 Clause"
            :url "https://opensource.org/licenses/BSD-3-Clause"}
  :dependencies [
                 [com.timezynk/domain "1.0.0"]            
                 [com.timezynk/useful "1.8.0" :scope "dev"]
                 [congomongo "2.1.0" :scope "provided"]
                 [org.clojure/clojure "1.10.0" :scope "provided"]
                 [slingshot "0.12.2"]
                 ]
  :repl-options {:init-ns domain-types.core})
