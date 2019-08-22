package core

private val commonEmit = CommonEmit()

fun <T> emit(event: EventType<T>, value: T) = commonEmit.emit(event, value)
fun <T> on(event: EventType<T>, callback: Callback<T>) = commonEmit.on(event, callback)
fun <T> on(event: EventType<T>, callback: Callback<T>, recentValue: Boolean = true)
        = commonEmit.on(event, callback, recentValue)
fun <T> cancel(event: EventType<T>, callback: Callback<T>) = commonEmit.cancel(event, callback)
suspend fun <T> getMostRecent(event: EventType<T>) = commonEmit.getMostRecent(event)
fun emit(event: SimpleEvent) = commonEmit.emit(event)
fun on(event: SimpleEvent, callback: () -> Unit, recentValue: Boolean = true)
        = commonEmit.on(event, callback, recentValue)
fun cancel(event: SimpleEvent, callback: () -> Unit) = commonEmit.cancel(event, callback)
