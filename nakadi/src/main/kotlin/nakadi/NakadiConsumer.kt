package nakadi

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import mu.KotlinLogging
import org.zalando.fahrschein.NakadiClient
import org.zalando.fahrschein.domain.Subscription
import java.net.URI

class NakadiConsumer(val eventName: String) {

    private var job: Thread? = null

    val logger = KotlinLogging.logger {}

    val nakadiClient = NakadiClient.builder(URI("http://localhost:8080"))
        .build()

    val jacksonObjectMapper = jacksonObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    var subscription: Subscription? = null

    var stopped = false

    fun startConsumer(listener: (List<OrderReceivedEvent>) -> Unit) {

        check(subscription == null) {
            "Already subscribed!"
        }

        job = Thread {
            val sub = nakadiClient.subscription("zalando-app", eventName).subscribe()
            subscription = sub

            logger.info { "starting consumer" }
            nakadiClient.stream(sub)
                .withObjectMapper(jacksonObjectMapper)
                .listen(OrderReceivedEvent::class.java) {
                    if (stopped) throw RuntimeException("stopped")

                    listener(it)
                }
            logger.info { "consumer done" }
        }.also { it.start() }
    }

    fun stopConsumer() {
        logger.info { "stop consumer $subscription" }

        stopped = true

        job?.interrupt()
        job?.join()

        subscription?.let {
            logger.info { "about to delete subscription!" }
            nakadiClient.deleteSubscription(it.id)
            logger.info { "deleted subscription" }
        }
    }

}