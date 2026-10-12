package com.swhurl.app

import io.micronaut.runtime.Micronaut

// A background worker with no HTTP server (Worker.kt). Starts the application context, whose scheduled
// work then runs, and waits; on SIGTERM Micronaut's shutdown hook closes the context.
fun main(args: Array<String>) {
    Micronaut.build(*args).banner(false).start()
    Thread.currentThread().join()
}
