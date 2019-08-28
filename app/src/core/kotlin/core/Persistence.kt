package core

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.get
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.orElse
import io.paperdb.Paper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

private val dispatcher = newSingleThreadContext("persistence") + logCoroutineExceptions()

interface Persistable {
    fun key(): String
}

interface Source<T> {
    fun <T> get(classOfT: Class<T>, id: String?): T?
    fun <T> get(id: String?): T?
    fun set(value: T, id: String?)
}

class PaperSource<T>(val key: String): Source<T> {

    override fun <T> get(classOfT: Class<T>, id: String?): T? {
        return if (id != null) paper().read("$key.$id")
        else paper().read(key)
    }

    override fun <T> get(id: String?): T? {
        return paper().read(key)
    }

    override fun set(value: T, id: String?) {
        paper().write(key, value)
    }

}

fun example() {
    val sampleInt = 0
    Register.sourceFor(sampleInt, "sampleInt", PaperSource<Int>("sample"))
}

object Register {

    //private val register = mutableMapOf<Pair<*, Any>, Any>()
    private val sources = mutableMapOf<Pair<*, String?>, Source<*>>()
    private val ctx = newSingleThreadContext("persistence") + logCoroutineExceptions()

    suspend fun <T> sourceFor(default: T, key: String, source: Source<T>) = withContext(ctx) {
        sources.put(default to key, source)
    }

    suspend fun <T> sourceFor(classOfT: Class<T>, key: String? = null, source: Source<T>) = withContext(ctx) {
        sources.put(classOfT to key, source)
    }

    suspend fun <T> get(default: T, key: String, id: String? = null) = withContext(ctx) {
        val source = sources.get(default to key) as Source<T>?
        source?.get(id) ?: default
    }

    suspend fun <T> get(classOfT: Class<T>, key: String? = null, id: String? = null) = withContext(ctx) {
        val source = sources.get(classOfT to key) as Source<T>?
        source?.get(classOfT, id)
    }

    suspend fun <T> set(value: T, key: String, id: String? = null) = withContext(ctx) {
        val source = sources.get(value to key) as Source<T>?
        source?.set(value, id)
    }

    suspend fun <T> set(classOfT: Class<T>, value: T, key: String? = null, id: String? = null) = withContext(ctx) {
        val source = sources.get(classOfT to key) as Source<T>?
        source?.set(value, id)
    }
}

fun <T> blockingResult(block: suspend CoroutineScope.() -> T) =
       runCatching { runBlocking { block() } }

suspend fun <T> T.loadFromPersistence(key: String): T {
    return withContext(dispatcher) {
        try {
            v("loading persistence", key)
            paper().read<T>(key)
        } catch (ex: Exception) {
            w("could not load persistence", key, ex)
            null
        } ?: this@loadFromPersistence
    }
}

suspend fun <T: Persistable> T.loadFromPersistence() = loadFromPersistence(this.key())

suspend fun <T> T.saveToPersistence(key: String) = withContext(dispatcher) {
    try {
        v("saving persistence", key)
        paper().write(key, this@saveToPersistence)
    } catch (ex: Exception) {
        w("could not save persistence", key, ex)
    }
    this@saveToPersistence
}

suspend fun <T: Persistable> T.saveToPersistence() = saveToPersistence(this.key())

fun <T> loadPersistence(name: String, default: () -> T) = Result.of {
    val value = paper().read(name, default())
    v("loaded persistence: $name")
    value
}.orElse {
    w("could not load persistence", name, it)
    Ok(default())
}.get()!!

fun savePersistence(name: String, value: Any) = Result.of {
    paper().write(name, value)
    v("saved persistence: $name")
}.onFailure {
    w("could not save persistence", name, it)
}


fun <T> get(classOfT: Class<T>, key: String? = null) =  runBlocking { loadPersistence(classOfT, key)!! }

suspend fun <T> loadPersistence(classOfT: Class<T>, key: String?): T? {
    return withContext(dispatcher) {
        try {
            v("reading persistance for", classOfT)
            paper().read()
            val persistor = persistors[classOfT] ?: throw Exception("no persistor defined")
            persistor.read(classOfT, key)
        } catch (ex: Exception) {
            e("could not read persistence", classOfT, ex)
            null
        }
    }
}

fun <T> parametrisedLoaderFor(identifier: Any, parameter: Any, type: T) = { identifier: Any, parameter: Any ->
    type
}

fun setPersistencePath(path: String) = Result.of {
    Paper.book().write("persistencePath", path)
    v("set persistence path", path)
}.onFailure {
    w("could not set persistence path", path, it)
}

fun isCustomPersistencePath() = loadPersistencePath() != ""

private fun loadPersistencePath() = Result.of {
    val path = Paper.book().read<String>("persistencePath", "")
    if (!pathLogged) {
        pathLogged = true
        v("using persistence path", path)
    }
    path
}.orElse {
    w("could not load persistence path", it)
    Ok("")
}.get()!!

private var pathLogged = false

private fun paper() = {
    with(loadPersistencePath()) {
        if (this != "") Paper.bookOn(this)
        else Paper.book()
    }
}()

