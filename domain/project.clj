(defproject com.timezynk/domain "1.0.1"
  :description "Database modeling library for Clojure and MongoDB"
  :url "https://github.com/TimeZynk/domain/tree/master/domain"
  :license {:name "BSD 3 Clause"
            :url "https://opensource.org/licenses/BSD-3-Clause"}
  :scm {:name "git"
        :url  "https://github.com/TimeZynk/domain"}
  :dependencies [[com.novemberain/validateur "1.2.0"]
                 [com.timezynk/useful "1.8.0"]
                 [org.clojure/clojure "1.10.1" :scope "provided"]
                 [slingshot "0.12.2"]
                 [com.timezynk/assembly-line "1.0.0"]
                 [compojure "1.5.1" :scope "provided"]
                 [congomongo "2.1.0" :scope "provided"]]
  :repl-options {:init-ns com.timezynk.domain.core}
  :plugins [[lein-cljfmt "0.6.7"]])
