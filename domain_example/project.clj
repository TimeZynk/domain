(defproject domain_example "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [compojure "1.2.0"]
                 [ring/ring-defaults "0.1.2"]
                 [com.timezynk.domain/domain-core "0.2.1-SNAPSHOT"]
                 [com.timezynk.domain/domain-http "0.2.1-SNAPSHOT"]
                 [com.timezynk.domain/domain-versioned-mongo "0.2.1-SNAPSHOT"]
                 ;;
                 [slingshot "0.12.1"]
                 [congomongo "0.4.4"]
                 [potemkin "0.3.11"]]
  :plugins [[lein-ring "0.8.13"]]
  :ring {:handler domain-example.core.handler/app}
  :profiles
  {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                        [ring-mock "0.1.5"]]}})
