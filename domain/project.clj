(defproject com.timezynk/domain "1.0.0-SNAPSHOT"
  :description "Database modeling library for Clojure and MongoDB"
  :url "https://github.com/TimeZynk/domain/tree/master/domain"
  :license {:name "BSD 3 Clause"
            :url "https://opensource.org/licenses/BSD-3-Clause"}
  :scm {:name "git"
        :url  "https://github.com/TimeZynk/domain"}
  :dependencies [
    [org.clojure/clojure "1.10.1" :scope "provided"]
    [com.timezynk/useful "1.7.0"]
  ]
  :repl-options {:init-ns domain.core})
