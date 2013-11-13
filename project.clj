(defproject clj-elasticsearch "0.5.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]]
  :profiles {:dev {:dependencies
                   [[org.elasticsearch/elasticsearch "0.20.5"]
                    [clj-elasticsearch-native "0.5.0-SNAPSHOT"]
                    [clj-elasticsearch-rest "0.5.0-SNAPSHOT"]]}})
