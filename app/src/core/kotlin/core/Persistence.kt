package core

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.get
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.orElse
import io.paperdb.Paper
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.withContext

private val dispatcher = newSingleThreadContext("persistence") + logCoroutineExceptions()

interface Persistable {
    fun key(): String
}

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

