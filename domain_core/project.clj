(defproject com.timezynk.domain.domain-core "0.1.0"
  :description "Core functionality of the domain project"
  :url "https://github.com/TimeZynk/domain/domain-core"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [compojure "1.1.5"]
                 [slingshot "0.10.3"]
                 [joda-time "2.1"]
                 [potemkin "0.3.4"]
                 [org.mongodb/mongo-java-driver "2.10.1"] ;used by validation, should be removed?
                 [com.timezynk.domain/assembly-line "0.1.0"]])
