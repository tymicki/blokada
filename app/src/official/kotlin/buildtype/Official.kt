package buildtype

import android.app.Application
import android.content.Context
import android.text.format.DateUtils
import android.util.Log
import core.getApplicationContext
import core.workerFor
import gs.environment.time
import gs.property.device
import gs.property.newPersistedProperty2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.blokada.R

private val kctx = workerFor("gscore")

private val j by lazy {
    runBlocking {
        Journal(getApplicationContext()!!)
    }
}

private val lastDailyMillis = newPersistedProperty2(kctx, "daily", { 0L },
        refresh = {
            j.event("daily")
            time.now()
        },
        shouldRefresh = { !DateUtils.isToday(it) })

private val lastActiveMillis = newPersistedProperty2(kctx, "daily-active", { 0L },
        refresh = {
            // TODO: once tunnel is avaibale
//            if (t.active()) {
//                j.event("daily-active")
//                time.now()
//            } else it
            it
        },
        shouldRefresh = { !DateUtils.isToday(it) })

suspend fun initBuildType() = withContext(Dispatchers.Main.immediate) {
    // I assume this will happen at least once a day
    device.screenOn.doWhenChanged().then {
        if (device.reports()) {
            lastDailyMillis.refresh()
            lastActiveMillis.refresh()
        }
    }

    // This will happen when loading the app to memory
    if (device.reports()) {
        lastDailyMillis.refresh()
        lastActiveMillis.refresh()
    }
}

class Journal(
        private val ctx: Context
) {

    private val amp by lazy {
        val a = JournalFactory.instance.initialize(ctx, ctx.getString(R.string.journal_key))

        try {
            val app = ctx as Application
            a.enableForegroundTracking(app)
        } catch (e: Exception) {
            Log.e("blokada", "journal: failed to get application", e)
        }
        a
    }

    fun event(vararg events: Any) {
        events.forEach { event ->
            amp.logEvent(event.toString())
            Log.i("blokada", "event: $event")
        }
    }

}

object JournalFactory {

    internal val instances: MutableMap<String, JournalClient> = HashMap()

    val instance: JournalClient
        get() = getInstance()

    @Synchronized
    fun getInstance(instance: String = ""): JournalClient {
        var instance = instance
        instance = Utils.normalizeInstanceName(instance!!)
        var client: JournalClient? = instances[instance]
        if (client == null) {
            client = JournalClient(instance)
            instances[instance] = client
        }
        return client
    }
}


