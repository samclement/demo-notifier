package com.swhurl.app

import io.micronaut.context.annotation.Value
import io.micronaut.scheduling.annotation.Scheduled
import jakarta.annotation.PostConstruct
import jakarta.inject.Singleton
import java.util.concurrent.atomic.AtomicLong
import org.slf4j.LoggerFactory

// A background worker with no web address. Its job is to listen: it receives demo-orders.created,
// the event demo-orders publishes for every order, and tells the team about a big one. It never
// talks to demo-orders and does not know where it runs: the platform's NATS carries the event, and
// this app declared only that it receives it (events.subscribe in swhurl.yaml).
// The work() loop of the template stays as a heartbeat. The platform restarts the pod if the process exits.
@Singleton
class Worker(
    @Value("\${otel.service.name:swhurl-app}") private val serviceName: String,
    private val events: Events,
    private val platform: Platform,
) {
    private val log = LoggerFactory.getLogger(Worker::class.java)
    val runs = AtomicLong()

    val received = AtomicLong()

    @PostConstruct
    fun listen() {
        for (name in events.declaredSubscriptions()) {
            events.subscribe(name) { event ->
                received.incrementAndGet()
                // The handler runs inside the publisher's trace: this line and the order's request share a trace id.
                log.info("event received: ${event.type} ${event.id} from ${event.source}")
                if (event.type == "demo-orders.created") {
                    bigOrder(event.data?.get("item")?.takeIf { it.isString }?.stringValue,
                             event.data?.get("quantity")?.takeIf { it.isNumber }?.intValue,
                             event.data?.get("placedBy")?.takeIf { it.isString }?.stringValue)?.let { message ->
                        // To the notification channel of the team that owns this app. The key makes a
                        // redelivered event one message, not two.
                        val sent = platform.notify("Big order", message = message, key = "order-${event.id}")
                        log.info("team told about a big order: $message (accepted: $sent)")
                    }
                }
            }
        }
    }

    @Scheduled(initialDelay = "0s", fixedDelay = "\${work.interval.ms:60000}ms")
    fun work() {
        try {
            runs.incrementAndGet()
            log.info("work done")
        } catch (error: Exception) {
            log.error("work failed", error)
        }
    }
}

/** What to tell the team about an order, or null for one that is not big (fewer than [BIG_ORDER]). */
const val BIG_ORDER = 10

fun bigOrder(item: String?, quantity: Int?, placedBy: String?): String? =
    if (quantity != null && quantity >= BIG_ORDER) "$quantity ${item ?: "of something"}, placed through ${placedBy ?: "an unknown app"}." else null
