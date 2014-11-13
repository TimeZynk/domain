(defproject com.timezynk.domain/domain-versioned-mongo "0.2.0"
  :description "Wrapper around congomongo. Works with domain."
  :url "https://github.com/TimeZynk/domain/tree/master/domain_versioned_mongo"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :scm {:name "git"
        :url  "https://github.com/TimeZynk/domain"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [com.timezynk.domain/domain-core "0.2.0"]
                 [congomongo "0.4.4"]
                 [joda-time "2.1"]]
  :profiles {:dev {:dependencies [[midje "1.6.3"]]}})
