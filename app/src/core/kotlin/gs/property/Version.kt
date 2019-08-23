package gs.property

import core.workerFor
import gs.environment.Worker
import kotlinx.coroutines.runBlocking
import org.blokada.BuildConfig

private val kctx = workerFor("gscore")

val version by lazy {
    runBlocking {
        VersionImpl(kctx)
    }
}

class VersionImpl(kctx: Worker) {

    val appName = newProperty(kctx, { "gs" })
    val name = newProperty(kctx, { "0.0" })
    val previousCode = newPersistedProperty2(kctx, "previous_code", { 0 })
    val nameCore = newProperty(kctx, { BuildConfig.VERSION_NAME })
    val obsolete = newProperty(kctx, { false })
}
