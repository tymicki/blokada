package core

import android.app.NotificationManager
import android.app.Service
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.blokada.R
import tunnel.tunnelState

val keepAlive by lazy {
    KeepAliveImpl()
}


class KeepAliveImpl {
    val keepAlive = newPersistedProperty2("keepAlive", { false })
}

suspend fun initKeepAlive() = withContext(Dispatchers.Main.immediate) {
    val ctx = getApplicationContext()!!
    // Start / stop the keep alive service depending on the configuration flag
    val keepAliveNotificationUpdater = { dropped: Int ->
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val n = createNotificationKeepAlive(ctx = ctx, count = dropped,
                last = tunnelState.tunnelRecentDropped().lastOrNull()
                        ?: ctx.getString(R.string.notification_keepalive_none)
        )
        nm.notify(3, n)
    }
    var w1: IWhen? = null
    var w2: IWhen? = null
    keepAlive.keepAlive.doWhenSet().then {
        if (keepAlive.keepAlive()) {
            tunnelState.tunnelDropCount.cancel(w1)
            w1 = tunnelState.tunnelDropCount.doOnUiWhenSet().then {
                keepAliveNotificationUpdater(tunnelState.tunnelDropCount())
            }
            tunnelState.enabled.cancel(w2)
            w2 = tunnelState.enabled.doOnUiWhenSet().then {
                keepAliveNotificationUpdater(tunnelState.tunnelDropCount())
            }
            keepAliveAgent.bind(ctx)
        } else {
            tunnelState.tunnelDropCount.cancel(w1)
            tunnelState.enabled.cancel(w2)
            keepAliveAgent.unbind(ctx)
        }
    }

    keepAlive.keepAlive {}
}

// So that it's never GC'd, not sure if it actually does anything
private val keepAliveAgent by lazy { KeepAliveAgent() }

class KeepAliveAgent {
    private val serviceConnection = object: ServiceConnection {
        @Synchronized override fun onServiceConnected(name: ComponentName, binder: IBinder) {}
        @Synchronized override fun onServiceDisconnected(name: ComponentName?) {}
    }

    fun bind(ctx: Context) {
        scheduleJob(ctx)
        val intent = Intent(ctx, KeepAliveService::class.java)
        intent.action = KeepAliveService.BINDER_ACTION
        ctx.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun unbind(ctx: Context) {
        try { unscheduleJob(ctx) } catch (e: Exception) {}
        try { ctx.unbindService(serviceConnection) } catch (e: Exception) {}
    }

    fun scheduleJob(ctx: Context) {
        val serviceComponent = ComponentName(ctx, BootJobService::class.java)
        val builder = JobInfo.Builder(1, serviceComponent)
        builder.setPeriodic(60 * 1000L)
        val jobScheduler = ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        jobScheduler.schedule(builder.build())
    }

    fun unscheduleJob(ctx: Context) {
        val jobScheduler = ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        jobScheduler.cancel(1)
    }
}

class KeepAliveService : Service() {

    companion object Statics {
        val BINDER_ACTION = "KeepAliveService"
    }

    class KeepAliveBinder : Binder()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        core.v("KeepAliveService: start command")
        return Service.START_STICKY
    }

    private var binder: KeepAliveBinder? = null
    override fun onBind(intent: Intent?): IBinder? {
        if (BINDER_ACTION.equals(intent?.action)) {
            binder = KeepAliveBinder()

            val count = tunnelState.tunnelDropCount()
            val last = tunnelState.tunnelRecentDropped().lastOrNull() ?: getString(R.string.notification_keepalive_none)
            val n = createNotificationKeepAlive(this, count, last)
            startForeground(3, n)

            core.v("KeepAliveService: bound")
            return binder
        }
        return null
    }

    override fun onUnbind(intent: Intent?): Boolean {
        binder = null
        stopForeground(true)
        core.v("KeepAliveService: unbind")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        core.v("KeepAliveService: destroy")
        // This is probably pointless
        if (binder != null) sendBroadcast(Intent("org.blokada.keepAlive"))
    }

}


