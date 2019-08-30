package ui

import android.app.Application
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import blocka.blokadaUserAgent
import blocka.initBlocka
import buildtype.initBuildType
import core.*
import dns.initDns
import filter.initFilters
import flavor.initFlavor
import g11n.TranslationStore
import io.paperdb.Paper
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import tunnel.*
import ui.bits.SlotsSeenStatus
import update.initUpdate


/**
 * Main.kt contains all entry points of the app.
 */

private fun startThroughJobScheduler(
        ctx: Context,
        scheduler: JobScheduler = ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
) {
    val serviceComponent = ComponentName(ctx, BootJobService::class.java)
    val builder = JobInfo.Builder(0, serviceComponent)
    builder.setOverrideDeadline(3 * 1000L)
    scheduler.schedule(builder.build())
}

class MainApplication: Application() {

    override fun onCreate() {
        super.onCreate()
        Paper.init(this)
        runBlocking { setApplicationContext() }
        repeat(10) { v("BLOKADA", "*".repeat(it * 2)) }
        v(blokadaUserAgent(this))
        setRestartAppOnCrash()

        runBlocking {
            Register.sourceFor("reports", SharedPreferencesSource("reports", default = false))
            Register.sourceFor("watchdogOn", SharedPreferencesSource("watchdogOn", default = false))
            Register.sourceFor("keepAlive", SharedPreferencesSource("keepAlive", default = false))
            Register.sourceFor("repo_refresh", SharedPreferencesSource("repo_refresh", default = 0L))
            Register.sourceFor("repo_url", SharedPreferencesSource("repo_url", default = ""))
            Register.sourceFor("dnsEnabled", SharedPreferencesSource("dnsEnabled", default = false))
            Register.sourceFor("locale", SharedPreferencesSource("locale", default = "en"))
            Register.sourceFor("lastSeenUpdate", SharedPreferencesSource("lastSeenUpdate", default = 0L))
            Register.sourceFor(BlockaConfig::class.java, PaperSource("blocka:config"),
                    default = BlockaConfig())
            Register.sourceFor(TunnelConfig::class.java, PaperSource("tunnel:config"),
                    default = TunnelConfig())
            Register.sourceFor(TunnelPause::class.java, PaperSource("tunnel:pause"),
                    default = TunnelPause())
            Register.sourceFor("rules:set", PaperSource("rules:set", template = "%s:%s"), default = Ruleset())
            Register.sourceFor("rules:size", PaperSource("rules:size", template = "%s:%s"), default = 0)
            Register.sourceFor(FilterStore::class.java, PaperSource("filters2"), default = FilterStore())
            Register.sourceFor(FiltersCache::class.java, PaperSource("filters2"), default = FiltersCache())
            Register.sourceFor("requests", PaperSource("requests", template = "%s:%s"), default = emptyList<Request>())
            Register.sourceFor(SlotsSeenStatus::class.java, PaperSource("slots:status"),
                    default = SlotsSeenStatus())
            Register.sourceFor(LoggerConfig::class.java, PaperSource("logger:config"),
                    default = LoggerConfig())
            Register.sourceFor(TranslationStore::class.java, PaperSource("g11n:translation:store"),
                    "g11n:translation:store", default = TranslationStore())
        }

        GlobalScope.launch {
            initDevice()
            initUpdate()
            initKeepAlive()
            initTunnel()
            initFilters()
            initDns()
            initUpdate()
            initApp()
            initBlocka()
            initUiBlokada()
            initFlavor()
            initBuildType()
        }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        Paper.init(this)
    }

    private fun setRestartAppOnCrash() {
        Thread.setDefaultUncaughtExceptionHandler { _, ex ->
            try {
                e(ex)
            } catch (e: Exception) {}
            startThroughJobScheduler(this)
            System.exit(2)
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent?) {
        v("received boot event")
        startThroughJobScheduler(ctx)
    }
}

class BootJobService : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        v("boot job start")
        device.connected.refresh()
        device.onWifi.refresh()
        return scheduleJobFinish(params)
    }

    private fun scheduleJobFinish(params: JobParameters?): Boolean {
        return try {
            when {
                tunnelState.active() -> {
                    v("boot job finnish immediately, already active")
                    false
                }
                !tunnelState.enabled() -> {
                    v("boot job finnish immediately, not enabled")
                    false
                }
                listener != null -> {
                    v("boot job finnish immediately, service waiting")
                    false
                }
                else -> {
                    v("boot job scheduling to stop when tunnel active")
                    listener = tunnelState.active.doOnUiWhenChanged().then {
                        tunnelState.active.cancel(listener)
                        listener = null
                        jobFinished(params, false)
                    }
                    true
                }
            }
        } catch (e: Exception) {
            e("boot job fail", e)
            false
        }
    }

    private var listener: IWhen? = null

    override fun onStopJob(params: JobParameters?): Boolean {
        return true
    }

}

val conflictingBuilds = listOf(
        "org.blokada.origin.alarm",
        "org.blokada.alarm",
        "org.blokada",
        "org.blokada.dev"
)
