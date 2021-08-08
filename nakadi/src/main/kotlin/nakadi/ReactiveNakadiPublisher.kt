package nakadi

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.channels.onSuccess
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.reactive.asFlow
import mu.KotlinLogging
import org.zalando.fahrschein.NakadiClient
import org.zalando.fahrschein.domain.Subscription
import reactor.core.publisher.Flux
import reactor.core.scheduler.Schedulers
import java.net.URI

class ReactiveNakadiPublisher(val eventName: String) {

    val logger = KotlinLogging.logger {}

    val nakadiClient =
        NakadiClient.builder(URI("http://localhost:8080"))
            .build()

    val jacksonObjectMapper =
        jacksonObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    var subscription: Subscription? = null

    fun subscribe() {
        logger.info { "subscribe" }
        subscription = nakadiClient.subscription("zalando-app", eventName).withConsumerGroup("reactive").subscribe()
        logger.info { "subscribed" }
    }

    fun unsubscribe() {
        subscription?.let {
            logger.info { "about to delete subscription!" }
            nakadiClient.deleteSubscription(it.id)
            logger.info { "deleted subscription" }
        }
    }

    fun orderReceivedEventsFlux(): Flux<List<OrderReceivedEvent>> =
        subscription?.let { sub ->
            Flux.create<List<OrderReceivedEvent>> { emitter ->
                logger.info { "create Flux" }

                val currentThread = Thread.currentThread()

                emitter.onDispose {
                    logger.info { "onDispose!" }
                    currentThread.interrupt()
                }

                try {
                    nakadiClient.stream(sub)
                        .withObjectMapper(jacksonObjectMapper)
                        .listen(OrderReceivedEvent::class.java) {
                            if (emitter.isCancelled) throw RuntimeException("closed")

                            emitter.next(it)
                        }
                } catch (e: InterruptedException) {
                }
            }.subscribeOn(Schedulers.boundedElastic())
        } ?: throw IllegalStateException()


    @OptIn(ExperimentalCoroutinesApi::class)
    fun orderReceivedEventsFlow(): Flow<List<OrderReceivedEvent>> =
        subscription?.let { sub ->
            callbackFlow<List<OrderReceivedEvent>> {
                logger.info { "create Flux" + Thread.currentThread() }

                val job = Thread {
                    try {
                        nakadiClient.stream(sub)
                            .withObjectMapper(jacksonObjectMapper)
                            .listen(OrderReceivedEvent::class.java) {
                                logger.info { "got $it" }
                                if (!isActive) throw RuntimeException("closed")

                                trySendBlocking(it)
                                    .onSuccess { }
                                    .onFailure { this@callbackFlow.cancel() }
                            }
                    } catch (e: InterruptedException) {
                    }
                }

                job.start()

                awaitClose {
                    logger.info { "onDispose! " + Thread.currentThread() }
                    job.interrupt()
                    job.join()
                }
            }.flowOn(Dispatchers.IO)
        } ?: throw IllegalStateException()
}