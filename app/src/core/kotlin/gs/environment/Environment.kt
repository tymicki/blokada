package gs.environment

import com.github.salomonbrys.kodein.LazyKodein
import nl.komponents.kovenant.buildDispatcher

@Deprecated("out")
typealias Environment = LazyKodein

val time = SystemTime()

@Deprecated("out")
class SystemTime {
    fun now(): Long {
        return System.currentTimeMillis()
    }
}

@Deprecated("out")
typealias Worker = nl.komponents.kovenant.Context

@Deprecated("out")
fun newSingleThreadedWorker(prefix: String): gs.environment.Worker {
    return nl.komponents.kovenant.Kovenant.createContext {
        callbackContext.dispatcher = buildDispatcher {
            name = "$prefix-callback"
            concurrentTasks = 1
            errorHandler = { core.e(it) }
            exceptionHandler = { core.e(it) }
        }
        workerContext.dispatcher = buildDispatcher {
            name = "$prefix-worker"
            concurrentTasks = 1
            errorHandler = { core.e(it) }
            exceptionHandler = { core.e(it) }
        }
    }
}

@Deprecated("out")
fun newConcurrentWorker(prefix: String, tasks: Int): gs.environment.Worker {
    return nl.komponents.kovenant.Kovenant.createContext {
        callbackContext.dispatcher = buildDispatcher {
            name = "$prefix-callbackX"
            concurrentTasks = 1
            errorHandler = { core.e(it) }
            exceptionHandler = { core.e(it) }
        }
        workerContext.dispatcher = buildDispatcher {
            name = "$prefix-workerX"
            concurrentTasks = tasks
            errorHandler = { core.e(it) }
            exceptionHandler = { core.e(it) }
        }
    }
}

