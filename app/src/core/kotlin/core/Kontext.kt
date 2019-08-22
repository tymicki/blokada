package core

import kotlinx.coroutines.Dispatchers.Unconfined
import kotlin.coroutines.CoroutineContext

open class Kontext internal constructor(
        private val id: Any,
        private val emit: Emit = DefaultEmit(id.toString()),
        val coroutineContext: () -> CoroutineContext = { throw Exception("coroutineContext not linked") }
): Emit by emit {

    companion object {
        fun new(id: Any) = Kontext(id)

        fun forCoroutine(coroutineContext: CoroutineContext, id: String) = Kontext(
                id = id,
                coroutineContext = { coroutineContext }
        )

        fun forTest(id: String = "test",
                    coroutineContext: CoroutineContext = Unconfined) = Kontext(
                id = id,
                coroutineContext = { coroutineContext },
                emit = CommonEmit(ktx = { Kontext("$id:emit",
                        coroutineContext = { coroutineContext }) })
        )
    }
}

fun String.ktx() = Kontext.new(this)
