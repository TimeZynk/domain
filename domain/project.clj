(defproject com.timezynk/domain "2.0.0"
  :description "Database modeling library for Clojure and MongoDB"
  :url "https://github.com/TimeZynk/domain/tree/master/domain"
  :license {:name "BSD 3 Clause"
            :url "https://opensource.org/licenses/BSD-3-Clause"}
  :scm {:name "git"
        :url  "https://github.com/TimeZynk/domain"}
  :dependencies [[ch.qos.logback/logback-classic "1.4.6"]
                 [com.novemberain/validateur "1.2.0"]
                 [com.timezynk/assembly-line "1.0.0"]
                 [com.timezynk/useful "1.21.0"]
                 [compojure "1.7.0" :scope "provided" :exclusions [commons-codec]]
                 [congomongo "2.6.0" :scope "provided"]
                 [org.clojure/clojure "1.11.1" :scope "provided"]
                 [org.clojure/tools.logging "1.2.4"]
                 [slingshot "0.12.2"]
                 [tortue/spy "2.13.0"]]
  :repl-options {:init-ns com.timezynk.domain.core}
  :test-paths ["src" "test"]
  :plugins [[lein-cljfmt "0.6.7"]]
  :profiles {:kaocha {:dependencies [[lambdaisland/kaocha "1.0.632"]]
                      :jvm-opts ["-Djdk.tls.client.protocols=TLSv1,TLSv1.1,TLSv1.2"]}}

  :jvm-opts ["-Dclojure.tools.logging.factory=clojure.tools.logging.impl/slf4j-factory"]
  :aliases {"kaocha" ["with-profile" "+kaocha" "run" "-m" "kaocha.runner"]})
