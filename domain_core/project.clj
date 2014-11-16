(defproject com.timezynk.domain/domain-core "0.2.1-SNAPSHOT"
  :description "Core functionality of the domain project"
  :url "https://github.com/TimeZynk/domain/tree/master/domain_core"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :scm {:name "git"
        :url  "https://github.com/TimeZynk/domain"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [slingshot "0.12.1"]
                 [potemkin "0.3.11"]
                 [com.timezynk.domain/assembly-line "0.2.0"]]
  :profiles {:dev {:dependencies [[midje "1.6.3"]]}})
