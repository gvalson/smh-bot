(defproject smh "0.1.0"
  :description "A telegram bot that notifies the user on new apartment listings"
  :url ""
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [org.clojure/tools.logging "1.2.4"]
                 [org.clojure/java.jdbc "0.7.12"]
                 [telegrambot-lib "2.6.0"]
                 [cheshire "5.11.0"]
                 [org.xerial/sqlite-jdbc "3.41.2.1"]
                 [clj-http "3.12.3"]
                 [org.clj-commons/hickory "0.7.3"]
                 [clojure.java-time "1.2.0"]]
  :main ^:skip-aot smh.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})
