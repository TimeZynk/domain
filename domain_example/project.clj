(defproject com.timezynk.domain/domain-example "0.2.1"

  :description "An example project, showing off domain"

  :url "https://github.com/TimeZynk/domain/domain-example"

  :license {:name "BSD 3-Clause License"
            :url "https://github.com/TimeZynk/domain/LICENSE"}

  :dependencies [[org.clojure/clojure "1.5.1"]
                 [ring/ring-servlet "1.2.0-beta1"]
                 [com.timezynk.domain/domain-core "0.2.1-SNAPSHOT"]
                 [com.timezynk.domain/domain-versioned-mongo "0.2.1-SNAPSHOT"]
                 [org.clojure/tools.logging "0.2.4"]
                 [joda-time "2.1"]]

  :repl-options {:init (do (use 'clojure.stacktrace
                                'clojure.pprint
                                'clojure.tools.logging))
                 :timeout 180000}


  :ring {:init               domain-example.ring/init
         :handler            domain-example.ring/app
         :destroy            domain-example.ring/destroy
         :servlet-path-info? false}

  :profiles {:dev {:dependecies    [[ring "1.2.1"]
                                    [midje "1.6.3"]]
                   :resource-paths ["resources-dev"]
                   :ring
                   {:init         domain-example.ring/init-dev
                    :handler      domain-example.ring/app
                    :destroy      domain-example.ring/destroy
                    :port         5555
                    :auto-reload? true}}}

  :min-lein-version "2.0.0"

  :plugins [[lein-ring "0.8.8"]])
