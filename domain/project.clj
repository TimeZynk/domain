(defproject com.timezynk/domain "2.4.0"
  :description "Database modeling library for Clojure and MongoDB"
  :url "https://github.com/TimeZynk/domain/tree/master/domain"
  :license {:name "BSD 3 Clause"
            :url "https://opensource.org/licenses/BSD-3-Clause"}
  :scm {:name "git"
        :url  "https://github.com/TimeZynk/domain"}
  :dependencies [[ch.qos.logback/logback-core "1.5.6" :scope "provided"]
                 [ch.qos.logback/logback-classic "1.5.6" :scope "provided"]
                 [ch.qos.logback.contrib/logback-jackson "0.1.5" :scope "provided"]
                 [ch.qos.logback.contrib/logback-json-classic "0.1.5" :scope "provided"]
                 [com.novemberain/validateur "1.2.0"]
                 [com.timezynk/assembly-line "1.0.1"]
                 [com.timezynk/bus "1.3.0" :scope "provided"]
                 [com.timezynk/cancancan "0.3.0" :scope "provided"]
                 [com.timezynk/domus "1.0.2" :scope "provided"]
                 [com.timezynk/useful "4.10.0" :scope "provided"]
                 [compojure "1.7.1" :scope "provided" :exclusions [commons-codec]]
                 [congomongo "2.6.0" :scope "provided"]
                 [org.clojure/clojure "1.11.1" :scope "provided"]
                 [slingshot "0.12.2"]
                 [tortue/spy "2.14.0"]]
  :repl-options {:init-ns com.timezynk.domain.core}
  :test-paths ["src" "test"]
  :plugins [[lein-cljfmt "0.6.7"]]
  :profiles {:kaocha {:dependencies [[lambdaisland/kaocha "1.0.632"]]
                      :jvm-opts ["-Djdk.tls.client.protocols=TLSv1,TLSv1.1,TLSv1.2"]}}
  :aliases {"kaocha" ["with-profile" "+kaocha" "run" "-m" "kaocha.runner"]})
