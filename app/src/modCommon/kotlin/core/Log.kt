package core

internal const val LOG_DEFAULT_TAG = "b4"
internal const val LOG_ERROR = 6
internal const val LOG_WARNING = 5
internal const val LOG_VERBOSE = 2
internal const val SEPARATOR = "   <--   "

private fun tag(tag: String) = "$LOG_DEFAULT_TAG:$tag"

private fun format(priority: Int, msg: Any, params: String)
        = "${indent(priority)}$msg$SEPARATOR$params"

private fun params(vararg msgs: Any) = msgs.drop(1).map { it.toString() }
        .plus(Thread.currentThread())
        .joinToString(", ")

private fun indent(priority: Int, level: Int = 0): String {
    var indent = 3
    if (priority == LOG_VERBOSE) indent += 4
    indent += 4 * level
    return " ".repeat(indent)
}

internal class DefaultLog(
        private val tag: String,
        private val writer: (Int, String, String) -> Any = defaultWriter,
        private val exceptionWriter: (Int, String, Throwable) -> Any = defaultExceptionWriter
) : Log {

    override fun e(vararg msgs: Any) {
        writer(LOG_ERROR, tag(tag), format(LOG_ERROR, msgs[0], params(*msgs)))
        msgs.filter { it is Throwable }.forEach {
            exceptionWriter(LOG_ERROR, tag(tag), it as Throwable)
        }
    }

    override fun w(vararg msgs: Any) {
        writer(LOG_WARNING, tag(tag), format(LOG_WARNING, msgs[0], params(*msgs)))
        msgs.filter { it is Throwable }.forEach {
            exceptionWriter(LOG_WARNING, tag(tag), it as Throwable)
        }
    }

    override fun v(vararg msgs: Any) {
        writer(LOG_VERBOSE, tag(tag), format(LOG_VERBOSE, msgs[0], params(*msgs)))
    }
}

internal val systemWriter = { priority: Int, tag: String, line: String ->
    if (priority == LOG_ERROR) System.err.println(tag + line)
    else System.out.println(tag + line)
}

internal val systemExceptionWriter = { priority: Int, tag: String, ex: Throwable ->
    ex.printStackTrace(if (priority == LOG_ERROR) System.err else System.out)
}

