(defproject event-data-twitter-compliance-logger "0.1.4"
  :description "Event Data Twitter Compliance Stream Logger"
  :url "http://eventdata.crossref.org"
  :license {:name "The MIT License (MIT)"
            :url "https://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/core.async "0.2.395"]
                 [yogthos/config "0.8"]
                 [org.clojure/tools.logging "0.3.1"]
                 [org.slf4j/slf4j-log4j12 "1.7.1"]
                 [org.clojure/tools.nrepl "0.2.12"]
                 [robert/bruce "0.8.0"]
                 [event-data-common "0.1.17"]
                 [clj-http "3.4.1"]
                 [org.clojure/data.json "0.2.6"]
                 [spootnik/signal "0.2.1"]]
  :main ^:skip-aot event-data-twitter-compliance-logger.core
  :java-source-paths ["src-java"]
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}
             :prod {:resource-paths ["resources" "config/prod"]}
             :dev {:resource-paths ["resources" "config/dev"]}})
