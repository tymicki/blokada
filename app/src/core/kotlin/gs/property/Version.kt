package gs.property

import core.workerFor
import gs.environment.Worker
import kotlinx.coroutines.runBlocking

val kctx = workerFor("gscore")

val version by lazy {
    runBlocking {
        VersionImpl(kctx)
    }
}

class VersionImpl(kctx: Worker) {
    val appName = newProperty(kctx, { "gs" })
    val name = newProperty(kctx, { "0.0" })
    val obsolete = newProperty(kctx, { false })
}
