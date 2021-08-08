package nakadi

import io.kotest.assertions.timing.eventually
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.restassured.RestAssured
import io.restassured.http.ContentType
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import mu.KotlinLogging
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class HelloNakadiSpec : StringSpec() {

    val logger = KotlinLogging.logger {}

    fun anOrderEvent(orderNumber: String) =
        """{
            "order_number": "$orderNumber",
            "metadata": {
            "eid": "${java.util.UUID.randomUUID()}",
            "occurred_at": "2016-03-15T23:47:15+01:00"
        }
        }"""

    fun anOrderEventList(vararg orderNumbers: String) =
        orderNumbers.map { anOrderEvent(it) }
            .joinToString(prefix = "[", postfix = "]") { it }


    val eventPayload = anOrderEventList("24873243241", "24873243242")

    fun publishEvents(payload: String) {
        RestAssured.given().contentType(ContentType.JSON)
            .body(payload)
            .post("http://localhost:8080/event-types/sales-order-placed/events")
            .then()
            .statusCode(200)

        logger.info { "published events" }
    }

    init {
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails()

        "can receive using simple nakadi client" {

            val results = mutableListOf<OrderReceivedEvent>()
            val consumer = NakadiConsumer("sales-order-placed")
            consumer.startConsumer {
                results.addAll(it)
                logger.info { "got $it" }
            }

            publishEvents(eventPayload)

            eventually(Duration.seconds(10)) {
                try {
                    results shouldContain OrderReceivedEvent("24873243241")
                    results shouldContain OrderReceivedEvent("24873243242")
                } catch (e: Exception) {
                    throw e
                }
            }

            consumer.stopConsumer()
        }


        "reactive" {

            val consumer = ReactiveNakadiPublisher("sales-order-placed")

            consumer.subscribe()

            try {
                consumer.orderReceivedEventsFlux()
                    .doOnError { logger.warn(it) { "error" } }
                    .doOnNext { logger.info { "process1 $it" } }
                    .subscribe()

                repeat(2) {
                    publishEvents(eventPayload)
                }

                logger.info { "published events" }

                Thread.sleep(5000)

            } finally {
                consumer.unsubscribe()
            }
        }

        "flow" {

            val consumer = ReactiveNakadiPublisher("sales-order-placed")

            consumer.subscribe()

            try {
                coroutineScope {
                    val consumerJob = launch {
                        consumer.orderReceivedEventsFlow()
                            .collect {
                                println(it)
                            }
                    }

                        repeat(2) {
                            publishEvents(eventPayload)
                        }

                        logger.info { "published events" }

                        delay(5000)
                        consumerJob.cancel()
                }
            } finally {
                consumer.unsubscribe()
            }
        }
    }
}
