package core

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.get
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.orElse
import io.paperdb.Paper

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

