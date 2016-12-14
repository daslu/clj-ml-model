(defproject com.zensols.ml/model "0.1.0-SNAPSHOT"
  :description "Interface for machine learning modeling, testing and training."
  :url "https://github.com/plandes/clj-ml-model"
  :license {:name "Apache License version 2.0"
            :url "https://www.apache.org/licenses/LICENSE-2.0"
            :distribution :repo}
  :plugins [[lein-codox "0.10.1"]
            [org.clojars.cvillecsteele/lein-git-version "1.0.3"]]
  :codox {:metadata {:doc/format :markdown}
          :project {:name "Interface for machine learning modeling, testing and training"}
          :output-path "target/doc/codox"
          :source-uri "https://github.com/plandes/clj-ml-model/blob/v{version}/{filepath}#L{line}"}
  :source-paths ["src/clojure"]
  :java-source-paths ["src/java"]
  :javac-options ["-Xlint:unchecked"]
  :dependencies [[org.clojure/clojure "1.8.0"]

                 ;; command line
                 [com.zensols.tools/actioncli "0.0.12"]

                 ;; dev
                 [com.zensols.gui/tabres "0.0.6"]

                 ;;; reports
                 [outpace/clj-excel "0.0.2"]
                 [org.clojure/data.csv "0.1.2"]
                 [com.zensols.tools/misc "0.0.4"]

                 ;; ML
                 [tw.edu.ntu.csie/libsvm "3.17"]
                 [nz.ac.waikato.cms.weka/weka-stable "3.6.13"]]
  :profiles {:appassem {:aot :all}
             :dev
             {:jvm-opts
              ["-Dlog4j.configurationFile=test-resources/log4j2.xml" "-Xms4g" "-Xmx12g" "-XX:+UseConcMarkSweepGC"]
              :dependencies [[nz.ac.waikato.cms.weka/weka-stable "3.6.13" :classifier "sources"]
                             [org.apache.logging.log4j/log4j-core "2.7"]
                             [org.apache.logging.log4j/log4j-slf4j-impl "2.7"]
                             [com.zensols/clj-append "1.0.5"]]}})
