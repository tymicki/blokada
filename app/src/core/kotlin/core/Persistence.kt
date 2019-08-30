package core

import android.content.Context
import io.paperdb.Paper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

interface Source<T> {
    fun <T> get(classOfT: Class<T>, id: String?): T?
    fun <T> get(id: String?): T?
    fun set(value: T, id: String?)
}

class PaperSource<T>(val key: String, val template: String = "%s.%s") : Source<T> {

    override fun <T> get(classOfT: Class<T>, id: String?): T? {
        return if (id != null) paper().read(template.format(key, id))
        else paper().read(key)
    }

    override fun <T> get(id: String?): T? {
        return if (id != null) paper().read(template.format(key, id))
        else paper().read(key)
    }

    override fun set(value: T, id: String?) {
        if (id != null) paper().write(template.format(key, id), value)
        else paper().write(key, value)
    }

}

class SharedPreferencesSource<T>(val key: String, val default: T) : Source<T> {

    private val p by lazy {
        val ctx = runBlocking { getApplicationContext()!! }
        ctx.getSharedPreferences("default", Context.MODE_PRIVATE)
    }

    override fun <T> get(classOfT: Class<T>, id: String?): T? {
        throw Exception("SharedPreferencesSource does not support get class")
    }

    override fun <T> get(id: String?): T? {
        return when (default) {
            is Boolean -> p.getBoolean(key, default)
            is Int -> p.getInt(key, default)
            is Long -> p.getLong(key, default)
            is String -> p.getString(key, default)
            else -> throw Exception("unsupported type for $key")
        } as T
    }

    override fun set(value: T, id: String?) {
        val e = p.edit()
        when(value) {
            is Boolean -> e.putBoolean(key, value)
            is Int -> e.putInt(key, value)
            is Long -> e.putLong(key, value)
            is String -> e.putString(key, value)
            else -> throw Exception("unsupported type for $key")
        }
        e.apply()
    }

}

object Register {

    private val defaults = mutableMapOf<Pair<*, String?>, Any?>()
    private val currents = mutableMapOf<Pair<*, String?>, Any?>()
    private val sources = mutableMapOf<Pair<*, String?>, Source<*>>()
    private val ctx = newSingleThreadContext("persistence") + logCoroutineExceptions()

    suspend fun <T> sourceFor(key: String, source: Source<T>, default: T? = null) = withContext(ctx) {
        sources.put(null to key, source)
        defaults.put(null to key, default as Any?)
        v("set source in register", key, source, default ?: "null")
    }

    suspend fun <T> sourceFor(classOfT: Class<T>, source: Source<T>, key: String? = null,
                              default: T? = null) = withContext(ctx) {
        sources.put(classOfT to key, source)
        defaults.put(classOfT to key, default as Any?)
        v("set source in register", classOfT, source, default ?: "null")
    }

    suspend fun <T> get(key: String, id: String? = null) = withContext(ctx) {
        val current = currents.get(null to key) as T?
        current ?: {
            val source = sources.get(null to key) as Source<T>?
            if (source == null) throw Exception("No source in register for: $key")
            source.get(id) as T? ?: {
                defaults.get(null to key) as T? ?: throw Exception("No default value in register for: $key")
            }()
        }()
    }

    suspend fun <T> get(classOfT: Class<T>, key: String? = null, id: String? = null) = withContext(ctx) {
        val current = currents.get(classOfT to key) as T?
        current ?: {
            val source = sources.get(classOfT to key) as Source<T>?
            if (source == null) throw Exception("No source in register for: $classOfT, (key: $key)")
            source.get(classOfT, id) as T? ?: defaults.get(classOfT to key) as T?
        }()
    }

    suspend fun <T> set(value: T, key: String, id: String? = null,
                        skipMemory: Boolean = false) = withContext(ctx) {
        val source = sources.get(null to key) as Source<T>?
        if (source == null) throw Exception("No source in register for: $key")
        source.set(value, id)
        v("persistence set", key, value as Any)
        if (!skipMemory) currents.put(null to key, value as Any)
    }

    suspend fun <T> set(classOfT: Class<T>, value: T, key: String? = null, id: String? = null,
                        skipMemory: Boolean = false) = withContext(ctx) {
        val source = sources.get(classOfT to key) as Source<T>?
        if (source == null) throw Exception("No source in register for: $classOfT, (key: $key)")
        source.set(value, id)
        v("persistence set", classOfT, value as Any)
        if (!skipMemory) currents.put(value to key, value as Any)
    }
}

fun <T> blockingResult(block: suspend CoroutineScope.() -> T) =
        runCatching { runBlocking { block() } }

fun <T> get(classOfT: Class<T>): T = runBlocking { Register.get(classOfT)!! }
fun <T> T.update(classOfT: Class<T>) = runBlocking { Register.set(classOfT, this@update) }

fun setPersistencePath(path: String) = runCatching {
    Paper.book().write("persistencePath", path)
    v("set persistence path", path)
}.onFailure {
    w("could not set persistence path", path, it)
}

fun isCustomPersistencePath() = loadPersistencePath() != ""

private fun loadPersistencePath() = runCatching {
    val path = Paper.book().read<String>("persistencePath", "")
    if (!pathLogged) {
        pathLogged = true
        v("using persistence path", if (path == "") "default" else path)
    }
    path
}.onFailure {
    w("could not load persistence path", it)
}.getOrDefault("")

private var pathLogged = false

private fun paper() = {
    with(loadPersistencePath()) {
        if (this != "") Paper.bookOn(this)
        else Paper.book()
    }
}()

