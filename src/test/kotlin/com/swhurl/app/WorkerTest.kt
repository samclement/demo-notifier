package com.swhurl.app

import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// The worker runs on its interval (100 ms here).
@MicronautTest
@Property(name = "work.interval.ms", value = "100")
class WorkerTest {
    @Inject
    lateinit var worker: Worker

    @Test
    fun `works on its interval`() {
        Thread.sleep(1000)
        assertTrue(worker.runs.get() >= 3, "ran ${worker.runs.get()} times in a second")
    }
}
