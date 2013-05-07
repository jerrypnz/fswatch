# fswatch

A file system watching library implemented with Java 7 File System API. 

## Usage

Add to your leningen dependencies:

```clojure
[fswatch "0.2.0-SNAPSHOT"]
```

Import the namespace:

```clojure
(require ['fswatch.core :as 'fs])
```

Please refer to `sample.clj` in the project for a simple demo.

Use `watch-path` to watch a directory. Specify the event you are
interested in and the event handler function using keyword arguments.
`watch-path` will automatically start a background task (if not started yet)
to poll the file system events.

```clojure
(fs/watch-path "/tmp" :create println :modify println :delete println)
```

The handler should be a single-arity function with a `java.nio.file.Path` as its
single argument.

Use `unwatch-path` to stop watching a directory. All the handler functions
registered on this directory are removed.

```clojure
(fs/unwatch-path "/tmp")
```

When you are done, you should use `stop-watchers` to stop the background task.

```clojure
(fs/stop-watchers)
```

Note that fswatch uses `future` for background tasks, so you may want to call
`shutdown-agents` when your application stops.

## License

Copyright © 2013 Jerry Peng.

Distributed under the Eclipse Public License, the same as Clojure.