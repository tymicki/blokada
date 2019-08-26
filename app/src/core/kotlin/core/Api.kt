package core

import android.app.Activity
import android.content.Context
import kotlinx.coroutines.Job
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection
import java.util.zip.GZIPInputStream

val COMMON = newFixedThreadPoolContext(Runtime.getRuntime().availableProcessors(), "common") + logCoroutineExceptions()

typealias Result<T> = com.github.michaelbull.result.Result<T, Exception>
typealias Time = Long
typealias Url = String

class EventType<T>(val name: String) {
    override fun toString() = name
}

typealias SimpleEvent = EventType<Unit>
typealias Callback<T> = (T) -> Unit

fun String.newEvent() = SimpleEvent(this)
fun <T> String.newEventOf() = EventType<T>(this)

interface Emit {
    fun <T> emit(event: EventType<T>, value: T): Job
    fun <T> on(event: EventType<T>, callback: Callback<T>): Job
    fun <T> on(event: EventType<T>, callback: Callback<T>, recentValue: Boolean = true): Job
    fun <T> cancel(event: EventType<T>, callback: Callback<T>): Job
    suspend fun <T> getMostRecent(event: EventType<T>): T?
    fun emit(event: SimpleEvent): Job
    fun on(event: SimpleEvent, callback: () -> Unit, recentValue: Boolean = true): Job
    fun cancel(event: SimpleEvent, callback: () -> Unit): Job
}

fun load(opener: () -> InputStream, lineProcessor: (String) -> String? = { it }): List<String> {
    val input = BufferedReader(InputStreamReader(opener()))

    val response = mutableListOf<String>()
    var line: String?

    try {
        do {
            line = input.readLine()
            if (line == null) break
            line = lineProcessor(line)
            if (line != null) response.add(line)
        } while (true)
    } finally {
        input.close()
    }

    return response
}

fun loadGzip(opener: () -> URLConnection, lineProcessor: (String) -> String? = { it }): List<String> {
    val input = createStream(opener())

    val response = mutableListOf<String>()
    var line: String?

    try {
        do {
            line = input.readLine()
            if (line == null) break
            line = lineProcessor(line)
            if (line != null) response.add(line)
        } while (true)
    } finally {
        input.close()
    }

    return response
}

fun createStream(con: URLConnection) = {
    val charset = "UTF-8"
    if (con.contentEncoding == "gzip" || con.url.file.endsWith(".gz")) {
        v("using gzip download", con.url)
        BufferedReader(InputStreamReader(GZIPInputStream(con.getInputStream()), charset))
    } else {
        BufferedReader(InputStreamReader(con.getInputStream(), charset))
    }
}()

fun openUrl(url: URL, timeoutMillis: Int): () -> HttpURLConnection = {
    val c = url.openConnection() as HttpURLConnection
    c.setRequestProperty("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_10_3) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/44.0.2403.155 Safari/537.36");
    c.setRequestProperty("Accept-Encoding", "gzip")
    c.connectTimeout = timeoutMillis
    c.readTimeout = timeoutMillis
    c.instanceFollowRedirects = true
    c
}

fun openFile(file: File): InputStream {
    return file.inputStream()
}

private val ctx = newSingleThreadContext("context-register") + logCoroutineExceptions()
private var appContext: WeakReference<Context?> = WeakReference(null)
private var activityContext: WeakReference<Activity?> = WeakReference(null)

suspend fun getActivityContext() = withContext(ctx) {
    activityContext.get() as Context
}

suspend fun getActivity() = withContext(ctx) {
    activityContext.get()
}

suspend fun getApplicationContext() = withContext(ctx) {
    appContext.get()
}

suspend fun Context.setApplicationContext() {
    withContext(ctx) {
        appContext = WeakReference(this@setApplicationContext)
    }
}

suspend fun Activity.setActivityContext() {
    withContext(ctx) {
        v("setting activity context", this@setActivityContext)
        activityContext = WeakReference(this@setActivityContext)
    }
}

suspend fun unsetActivity() = withContext(ctx) {
    v("unsetting activity context")
    activityContext = WeakReference(null)
}

