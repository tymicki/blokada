package core

import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

private data class TypedEvent<T>(val type: EventType<T>, val value: T)

internal class CommonEmit(
        private val context: CoroutineContext = Dispatchers.Main + newEmitExceptionLogger()
) : Emit {

    private val emits = mutableMapOf<EventType<*>, TypedEvent<*>>()
    private val callbacks = mutableMapOf<EventType<*>, MutableList<Callback<*>>>()
    private val simpleEmits = mutableSetOf<EventType<Unit>>()
    private val simpleCallbacks = mutableMapOf<EventType<Unit>, MutableList<() -> Unit>>()

    override fun <T> emit(event: EventType<T>, value: T) = GlobalScope.launch(context) {
        v("event:emit", event, value.toString())
        val e = TypedEvent(event, value)
        emits[event] = e

        if (callbacks.containsKey(event)) for (callback in callbacks[event]!!)
            (callback as Callback<T>)(e.value)
    }

    override fun <T> on(event: EventType<T>, callback: Callback<T>) = on(event, callback, recentValue = true)

    override fun <T> on(event: EventType<T>, callback: Callback<T>, recentValue: Boolean) = GlobalScope.launch(context) {
        v("event:subscriber:on", event, callback)
        callbacks.getOrPut(event, { mutableListOf() }).add(callback as Callback<*>)
        if (recentValue) emits[event]?.apply { callback(this.value as T) }
    }

    override fun <T> cancel(event: EventType<T>, callback: Callback<T>) = GlobalScope.launch(context) {
        v("event:subscriber:cancel", event, callback)
        callbacks[event]?.remove(callback)
    }

    override fun emit(event: SimpleEvent) = GlobalScope.launch(context) {
        v("event emit", event)
        simpleEmits.add(event)

        if (simpleCallbacks.containsKey(event)) for (callback in simpleCallbacks[event]!!)
            callback()
    }

    override fun on(event: SimpleEvent, callback: () -> Unit, recentValue: Boolean) = GlobalScope.launch(context) {
        v("event:subscriber:on", event, callback)
        simpleCallbacks.getOrPut(event, { mutableListOf() }).add(callback)
        if (recentValue) emits[event]?.apply { callback() }
    }

    override fun cancel(event: SimpleEvent, callback: () -> Unit) = GlobalScope.launch(context) {
        v("event:subscriber:cancel", event, callback)
        simpleCallbacks[event]?.remove(callback)
    }

    override suspend fun <T> getMostRecent(event: EventType<T>) = GlobalScope.async(context) {
        emits[event]
    }.await()?.value as T?

}

internal fun newEmitExceptionLogger() = CoroutineExceptionHandler { _, throwable -> e(throwable) }

