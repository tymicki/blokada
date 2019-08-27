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
import io.paperdb.Paper
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import tunnel.initTunnel
import tunnel.tunnelState
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

