(ns fswatch.core
  (:require [clojure.set :refer [subset?]])
  (:import (java.nio.file FileSystems
                          Path
                          Paths
                          StandardWatchEventKinds
                          WatchService
                          WatchKey
                          WatchEvent)
           (java.util.concurrent TimeUnit)))

(def ^:private kw-to-event
  {:create StandardWatchEventKinds/ENTRY_CREATE
   :delete StandardWatchEventKinds/ENTRY_DELETE
   :modify StandardWatchEventKinds/ENTRY_MODIFY})

(def ^:private event-to-kw (->> kw-to-event
                                (map (fn [[k v]] [v k]))
                                (into {})))

(def ^:dynamic *watch-timeout* 1)

(def ^:private initial-stats {:watcher nil
                              :fs nil
                              :watching-paths {}})
(def ^:private watch-stats (atom initial-stats))
(def ^:private running (atom false))

(defn- get-or-create-watch-service
  []
  (if-let [watcher (:watcher @watch-stats)]
    watcher
    (let [fs (FileSystems/getDefault)
          watcher (.newWatchService fs)]
      (swap! watch-stats merge {:watcher watcher :fs fs})
      watcher)))

(defn- handle-watch-events
  [watch-key handlers]
  (let [events (.pollEvents watch-key)]
    (doseq [ev events]
      (let [kind (.kind ev)
            ctx (.context ev)
            handler-key (event-to-kw kind)
            handler (handlers handler-key)]
        (handler ctx)))
    (.reset watch-key)))

(defn- poll-events
  [watcher]
  (if-let [watch-key (.poll watcher
                            *watch-timeout*
                            TimeUnit/MINUTES)]
    (future
      (let [path (.watchable watch-key)
            handlers (get-in @watch-stats
                             [:watching-paths
                              path
                              :handlers])]
        (handle-watch-events watch-key handlers)))))

(defn- start-watcher
  []
  (when (compare-and-set! running false true)
    (future
      (let [watcher (:watcher @watch-stats)]
        (while @running
          (poll-events watcher))))))

(defn- cancel-watch-key
  [watch-key handlers]
  (.cancel watch-key)
  (handle-watch-events watch-key handlers))

(defn- get-path
  [fs pathname]
  (.getPath fs pathname (make-array String 0)))

(defn arg-count
  "Uses reflection to work out how many arguments a function takes"
  [f]
  (let [m (first (.getDeclaredMethods (class f)))
        p (.getParameterTypes m)]
    (alength p)))

(defn validate-handlers
  "Makes sure that the handlers map contains only keys we have an event
   for and only single arity functions, else throw"
  [handlers]
  (when-not (subset? (set (keys handlers)) (set (keys kw-to-event)))
    (throw (IllegalArgumentException. (str "Unsupported handler event(s) " (keys handlers)
                                           " known handlers are " (keys kw-to-event)))))
  (when-not (every? (fn [f] (= 1 (arg-count f))) (vals handlers))
    (throw (IllegalArgumentException. "Handlers must be functions which take a single parameter (the file affected)"))))

(defn watch-path
  "Start watching a path and call the handlers if files are
   created/modified/deleted in that directory.  If the watcher thread is
   not started, this function automatically starts it.

   The handlers are given via keyword args, currently supported keywords
   are :create, :modify and :delete.

   Handler functions should take a single argument, the File affected by
   the change."
  [pathname & {:as handlers}]
  (validate-handlers handlers)
  (let [watcher (get-or-create-watch-service)
        fs (:fs @watch-stats)
        path (get-path fs pathname)
        watch-events (->> handlers
                          (map (comp kw-to-event first))
                          into-array)
        watch-key (.register path
                             watcher
                             watch-events)]
    (swap! watch-stats
           assoc-in
           [:watching-paths path]
           {:handlers handlers :watch-key watch-key})
    (start-watcher)))

(defn unwatch-path
  "Stop watching a particular path"
  [pathname]
  (let [path (get-path (:fs @watch-stats) pathname)
        {:keys [watch-key handlers]} (get-in @watch-stats
                                             [:watching-paths path])]
    (when watch-key
      (cancel-watch-key watch-key handlers)
      (swap! watch-stats update-in [:watching-paths] dissoc path))))

(defn stop-watchers
  "Close all the watching services and stop the polling threads"
  []
  (let [{:keys [watcher watching-paths handlers]} @watch-stats]
    (doseq [[_ {:keys [watch-key handlers]}] watching-paths]
      (cancel-watch-key watch-key handlers))
    (.close watcher)
    (reset! running false)
    (reset! watch-stats initial-stats)))
