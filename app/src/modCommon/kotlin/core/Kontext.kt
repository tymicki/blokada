package core

import kotlinx.coroutines.experimental.Unconfined
import kotlin.coroutines.experimental.CoroutineContext

open class Kontext internal constructor(
        private val id: Any,
        private val log: Log = DefaultLog(id.toString()),
        private val emit: Emit = DefaultEmit(id.toString(), log = log),
        val coroutineContext: () -> CoroutineContext = { throw Exception("coroutineContext not linked") }
): Log by log, Emit by emit {

    companion object {
        fun new(id: Any) = Kontext(id)

        fun forCoroutine(coroutineContext: CoroutineContext, id: String,
                         log: Log = DefaultLog(id.toString())) = Kontext(
                id = id,
                log = log,
                coroutineContext = { coroutineContext }
        )

        fun forTest(id: String = "test", log: Log = DefaultLog(id, writer = systemWriter,
                exceptionWriter = systemExceptionWriter),
                    coroutineContext: CoroutineContext = Unconfined) = Kontext(
                id = id,
                log = log,
                coroutineContext = { coroutineContext },
                emit = CommonEmit(ktx = { Kontext("$id:emit", log = log,
                        coroutineContext = { coroutineContext }) })
        )
    }
}

fun String.ktx() = Kontext.new(this)
