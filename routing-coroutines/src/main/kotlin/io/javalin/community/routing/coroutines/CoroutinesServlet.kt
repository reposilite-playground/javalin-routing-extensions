package io.javalin.community.routing.coroutines

import io.javalin.community.routing.coroutines.ktor.CoroutineNameRepresentation
import io.javalin.community.routing.coroutines.ktor.DefaultUncaughtExceptionHandler
import io.javalin.http.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.EmptyCoroutineContext

open class CoroutinesServlet<CONTEXT, RESPONSE : Any>(
    name: String = "javalin-reactive-routing",
    private val coroutinesEnabled: Boolean = true,
    protected val dispatcher: CoroutineDispatcher,
    protected val syncHandler: suspend (Context, ReactiveRoute<CONTEXT, RESPONSE>) -> RESPONSE,
    protected val asyncHandler: suspend (Context, ReactiveRoute<CONTEXT, RESPONSE>, CompletableFuture<RESPONSE>) -> RESPONSE,
    private val responseConsumer: (suspend (Context, RESPONSE) -> Unit)? = null,
    errorConsumer: (CoroutineNameRepresentation, Throwable) -> Unit,
) {

    private val coroutineName: CoroutineName = CoroutineName(name)
    private val exceptionHandler = DefaultUncaughtExceptionHandler(errorConsumer)
    private val scope = JavalinCoroutineScope(EmptyCoroutineContext, exceptionHandler)
    private val id = AtomicLong()
    private val finished = AtomicLong()

    fun handle(ctx: Context, route: ReactiveRoute<CONTEXT, RESPONSE>) {
        id.incrementAndGet()

        when {
            coroutinesEnabled && route.async -> {
                ctx.future {
                    val result = CompletableFuture<RESPONSE>()

                    scope.launch(dispatcher + coroutineName) {
                        runCatching { asyncHandler(ctx, route, result) }
                            .map {
                                responseConsumer?.invoke(ctx, it)
                                result.complete(it)
                            }
                            .onFailure { result.completeExceptionally(it) }
                        finished.incrementAndGet()
                    }

                    result
                }
            }
            else -> runBlocking {
                val response = syncHandler(ctx, route)
                responseConsumer?.invoke(ctx, response)
                finished.incrementAndGet()
            }
        }
    }

    fun countFinishedTasks(): Long =
        finished.get()

    fun countExecutedTasks(): Long =
        id.get()

}