(defproject com.timezynk.domain/domain-http "0.2.1-SNAPSHOT"

  :description "Use domain with ring"

  :url "http://todo"

  :license {:name "BSD 3-Clause License"
            :url "https://github.com/TimeZynk/domain/LICENSE"}

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [com.timezynk.domain/domain-core "0.2.1-SNAPSHOT"]
                 [com.timezynk.domain/assembly-line "0.2.0"]
                 [compojure "1.2.1"]
                 [joda-time "2.0"]]

  :profiles {:dev {:dependencies [[midje "1.6.3"]]}})
