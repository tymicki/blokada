package core

import kotlinx.coroutines.*

private val commonEmit = CommonEmit()

private data class TypedEvent<T>(val type: EventType<T>, val value: T)

internal class CommonEmit(
        private val ktx: () -> Kontext = { Kontext.forCoroutine(Dispatchers.Main + newEmitExceptionLogger(), "emit") }
) : Emit {

    private val emits = mutableMapOf<EventType<*>, TypedEvent<*>>()
    private val callbacks = mutableMapOf<EventType<*>, MutableList<Callback<*>>>()
    private val simpleEmits = mutableSetOf<EventType<Unit>>()
    private val simpleCallbacks = mutableMapOf<EventType<Unit>, MutableList<() -> Unit>>()

    override fun <T> emit(event: EventType<T>, value: T) = GlobalScope.launch(ktx().coroutineContext()) {
        val e = TypedEvent(event, value)
        emits[event] = e

        if (callbacks.containsKey(event)) for (callback in callbacks[event]!!)
            (callback as Callback<T>)(e.value)
    }

    override fun <T> on(event: EventType<T>, callback: Callback<T>) = on(event, callback, recentValue = true)

    override fun <T> on(event: EventType<T>, callback: Callback<T>, recentValue: Boolean) = GlobalScope.launch(ktx().coroutineContext()) {
        callbacks.getOrPut(event, { mutableListOf() }).add(callback as Callback<*>)
        if (recentValue) emits[event]?.apply { callback(this.value as T) }
    }

    override fun <T> cancel(event: EventType<T>, callback: Callback<T>) = GlobalScope.launch(ktx().coroutineContext()) {
        callbacks[event]?.remove(callback)
    }

    override fun emit(event: SimpleEvent) = GlobalScope.launch(ktx().coroutineContext()) {
        simpleEmits.add(event)

        if (simpleCallbacks.containsKey(event)) for (callback in simpleCallbacks[event]!!)
            callback()
    }

    override fun on(event: SimpleEvent, callback: () -> Unit, recentValue: Boolean) = GlobalScope.launch(ktx().coroutineContext()) {
        simpleCallbacks.getOrPut(event, { mutableListOf() }).add(callback)
        if (recentValue) emits[event]?.apply { callback() }
    }

    override fun cancel(event: SimpleEvent, callback: () -> Unit) = GlobalScope.launch(ktx().coroutineContext()) {
        simpleCallbacks[event]?.remove(callback)
    }

    override suspend fun <T> getMostRecent(event: EventType<T>) = GlobalScope.async(ktx().coroutineContext()) {
        emits[event]
    }.await()?.value as T?

}

internal fun newEmitExceptionLogger(ktx: Kontext = "emit:exception".ktx())
        = CoroutineExceptionHandler { _, throwable -> e(throwable)
}

internal class DefaultEmit(id: String, val common: Emit = commonEmit) : Emit {

    override fun <T> emit(event: EventType<T>, value: T): Job {
        v("event:emit", event, value.toString())
        return common.emit(event, value)
    }

    override fun <T> on(event: EventType<T>, callback: Callback<T>) = on(event, callback, true)

    override fun <T> on(event: EventType<T>, callback: Callback<T>, recentValue: Boolean): Job {
        v("event:subscriber:on", event, callback)
        return common.on(event, callback, recentValue)
    }

    override fun <T> cancel(event: EventType<T>, callback: Callback<T>): Job {
        v("event:subscriber:cancel", event, callback)
        return common.cancel(event, callback)
    }

    override suspend fun <T> getMostRecent(event: EventType<T>) = common.getMostRecent(event)

    override fun emit(event: SimpleEvent): Job {
        v("event emit", event)
        return common.emit(event)
    }

    override fun on(event: SimpleEvent, callback: () -> Unit, recentValue: Boolean): Job {
        v("event:subscriber:on", event, callback)
        return common.on(event, callback)
    }

    override fun cancel(event: SimpleEvent, callback: () -> Unit): Job {
        v("event:subscriber:cancel", event, callback)
        return common.cancel(event, callback)
    }
}
