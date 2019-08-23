package core

import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.*

fun e(vararg msgs: Any) {
    write(PRIORITY_ERROR, tag(), line(msgs[0], params(*msgs)))
    msgs.filter { it is Throwable }.forEach {
        writeException(PRIORITY_ERROR, tag(), it as Throwable)
    }
}

fun w(vararg msgs: Any) {
    write(PRIORITY_WARNING, tag(), line(msgs[0], params(*msgs)))
    msgs.filter { it is Throwable }.forEach {
        writeException(PRIORITY_WARNING, tag(), it as Throwable)
    }
}

fun v(vararg msgs: Any) {
    write(PRIORITY_VERBOSE, tag(), line(msgs[0], params(*msgs)))
}

private const val TAG_TEMPLATE = "b4:%s"
private const val LINE_TEMPLATE = "%s    %s"
private const val FILE_LINE_TEMPLATE = "%s%s%s%s"
private const val PRIORITY_ERROR = 6
private const val PRIORITY_WARNING = 5
private const val PRIORITY_VERBOSE = 2

private fun tag() = TAG_TEMPLATE.format(Thread.currentThread().name)

private fun line(msg: Any, params: String) = LINE_TEMPLATE.format(msg, params)

private fun params(vararg msgs: Any) = msgs
        .drop(1)
        .map { it.toString() }
        .joinToString(", ")

private fun write(priority: Int, tag: String, line: String) {
    android.util.Log.println(priority, tag, line)
    try { logFile?.println(FILE_LINE_TEMPLATE.format(
            time(), priorityToLetter(priority), tag, line
    )) } catch (ex: Exception) {}
}

private fun writeException(priority: Int, tag: String, ex: Throwable) {
    android.util.Log.println(priority, tag, android.util.Log.getStackTraceString(ex))
    try {
        ex.printStackTrace(logFile)
    } catch (e: Exception) {}
}

private val logFile by lazy {
    try {
        android.util.Log.println(android.util.Log.VERBOSE, tag(), "setting up log file")
        val context = runBlocking { getApplicationContext() }!!
        val path = File(context.filesDir, "blokada.log")
        val writer = PrintWriter(FileOutputStream(path, true), true)
        if (path.length() > 4 * 1024 * 1024) path.delete()
        android.util.Log.println(android.util.Log.VERBOSE, tag(),
                "writing logs to file: ${path.canonicalPath}")
        writer
    } catch (ex: Exception) {
        android.util.Log.println(android.util.Log.WARN, tag(), "failed to open log file")
        android.util.Log.println(android.util.Log.WARN, tag(),
                android.util.Log.getStackTraceString(ex)
        )
        null
    }
}

private val dateFormatter = SimpleDateFormat("MM-dd HH:mm:ss.SSS")

private fun priorityToLetter(priority: Int) = when(priority) {
    PRIORITY_ERROR -> " E "
    PRIORITY_WARNING -> " W "
    else -> " V "
}

private fun time() = dateFormatter.format(Date())
