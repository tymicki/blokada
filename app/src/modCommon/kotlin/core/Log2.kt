
import android.content.Context
import core.LOG_DEFAULT_TAG
import core.LOG_VERBOSE
import core.LOG_WARNING
import core.Result
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.*

private val logcatWriter = { priority: Int, tag: String, line: String ->
    android.util.Log.println(priority, tag, line)
}

private val logcatExceptionWriter = { priority: Int, tag: String, ex: Throwable ->
    android.util.Log.println(priority, tag, android.util.Log.getStackTraceString(ex))
}


class FileLogWriter {

    lateinit var ctx: Context

    private val file by lazy {
        try {
            val path = File(ctx.filesDir, "blokada.log")
            val writer = PrintWriter(FileOutputStream(path, true), true)
            if (path.length() > 4 * 1024 * 1024) path.delete()
            logcatWriter(LOG_VERBOSE, LOG_DEFAULT_TAG, "writing logs to file: ${path.canonicalPath}")
            writer
        } catch (ex: Exception) {
            logcatWriter(LOG_WARNING, LOG_DEFAULT_TAG, "fail opening log file: ${ex.message}")
            null
        }
    }

    @Synchronized internal fun writer(priority: Int, tag: String, line: String) {
        Result.of { file!!.println(time() + priority(priority) + tag + line) }
        logcatWriter(priority, tag, line)
    }

    @Synchronized internal fun exceptionWriter(priority: Int, tag: String, ex: Throwable) {
        Result.of { ex.printStackTrace(file) }
        logcatExceptionWriter(priority, tag, ex)
    }

    private val formatter = SimpleDateFormat("MM-dd HH:mm:ss.SSS")
    private fun time() = formatter.format(Date())

    private fun priority(priority: Int) = when(priority) {
        6 -> " E "
        5 -> " W "
        else -> " V "
    }
}

