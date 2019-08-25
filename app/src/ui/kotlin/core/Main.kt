package core

import android.app.Application
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import buildtype.initBuildType
import com.github.salomonbrys.kodein.Kodein
import com.github.salomonbrys.kodein.KodeinAware
import com.github.salomonbrys.kodein.lazy
import flavor.initFlavor
import gs.environment.newGscoreModule
import gs.property.IWhen
import gs.property.device
import gs.property.newDeviceModule
import io.paperdb.Paper
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import tunnel.blokadaUserAgent
import tunnel.initTunnel
import tunnel.newRestApiModule


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

class MainApplication: Application(), KodeinAware {

    override val kodein by Kodein.lazy {
        import(newGscoreModule(this@MainApplication))
        import(newDeviceModule(this@MainApplication))
        import(newWelcomeModule(this@MainApplication))
        import(newUpdateModule(this@MainApplication))
        import(newKeepAliveModule(this@MainApplication))
        import(newBatteryModule(this@MainApplication))
        import(newRestApiModule(this@MainApplication))
        import(newAppModule(this@MainApplication), allowOverride = true)
    }

    override fun onCreate() {
        super.onCreate()
        Paper.init(this)
        runBlocking { setApplicationContext() }
        repeat(10) { v("BLOKADA", "*".repeat(it * 2)) }
        v(blokadaUserAgent(this))
        setRestartAppOnCrash()

        GlobalScope.launch {
            //welcome
            initTunnel()
            initFilters()
            initDns()
            //update
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

    private val ktx by lazy { "boot:service".ktx() }

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

