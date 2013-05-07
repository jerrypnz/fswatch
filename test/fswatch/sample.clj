(ns fswatch.sample
  (:require [fswatch.core :as fs]))


(defn watch-and-print [path]
  (println "Start watching" path "...")
  (fs/watch-path
   path
   :create #(println "File created: "
                     (-> % .toFile .getAbsolutePath))
   :modify #(println "File modified: "
                     (-> % .toFile .getAbsolutePath))
   :delete #(println "File deleted: "
                     (-> % .toFile .getAbsolutePath)))
  
  (Thread/sleep 60000)
  
  (println "Stopping watchers..")
  (fs/stop-watchers)
  (shutdown-agents))
