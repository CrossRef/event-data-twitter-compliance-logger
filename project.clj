(defproject event-data-twitter-compliance-logger "0.1.0"
  :description "Event Data Twitter Compliance Stream Logger"
  :url "http://eventdata.crossref.org"
  :license {:name "The MIT License (MIT)"
            :url "https://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [yogthos/config "0.8"]
                 [org.clojure/tools.logging "0.3.1"]
                 [org.clojure/tools.nrepl "0.2.12"]
                 [org.apache.httpcomponents/httpcore "4.4.5"]
                 [org.apache.httpcomponents/httpclient "4.5.2"]
                 [com.amazonaws/aws-java-sdk "1.11.46"]
                 [javax/javaee-api "7.0"]]
  :main ^:skip-aot event-data-twitter-compliance-logger.core
  :java-source-paths ["src-java"]
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}
             :prod {:resource-paths ["config/prod"]}
             :dev {:resource-paths ["config/dev"]}})
