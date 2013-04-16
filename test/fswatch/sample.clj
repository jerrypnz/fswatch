(ns fswatch.sample
  (:use fswatch.core))
 
(defn print-ev
  [ev ctx]
  (println "[fswatch]" ev " --> " ctx))

(defn watch-and-print [path]
  (println "Start watching" path "...")
  (watch-path path
              :create print-ev
              :modify print-ev
              :delete print-ev)
  
  (Thread/sleep 60000)
  
  (println "Stopping watchers..")
  (stop-watchers)
  (shutdown-agents))
