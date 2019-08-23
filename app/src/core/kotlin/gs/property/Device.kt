package gs.property

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.PowerManager
import com.github.salomonbrys.kodein.*
import core.getApplicationContext
import core.v
import core.workerFor
import gs.environment.*
import kotlinx.coroutines.runBlocking
import nl.komponents.kovenant.Kovenant
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task
import java.net.InetSocketAddress
import java.net.Socket

private val kctx = workerFor("gscore") // Was 2 threads
private val kctxWatchdog = workerFor("watchdog")

val device by lazy {
    runBlocking {
        DeviceImpl(kctx, getApplicationContext()!!)
    }
}

val watchdog by lazy {
    runBlocking {
        Watchdog()
    }
}

class DeviceImpl (
        kctx: Worker,
        ctx: Context
) {

    fun isWaiting(): Boolean {
        return !connected()
    }

    private val pm: PowerManager by lazy {
        runBlocking {
            getApplicationContext()!!.getSystemService(Context.POWER_SERVICE) as PowerManager
        }
    }

    val appInForeground = newProperty(kctx, { false })
    val screenOn = newProperty(kctx, { pm.isInteractive })
    val connected = newProperty(kctx, zeroValue = { true }, refresh = {
        // With watchdog off always returning true, we basically disable detection.
        // Because isConnected sometimes returns false when we are actually online.
        val c = isConnected(ctx) or watchdog.test()
        v("connected", c)
        c
    } )
    val tethering = newProperty(kctx, { isTethering(ctx)} )

    val watchdogOn = newPersistedProperty2(kctx, "watchdogOn",
            { false })

    val onWifi = newProperty(kctx, { isWifi(ctx) } )

    val reports = newPersistedProperty2(kctx, "reports",
            { true }
    )

}

class ConnectivityReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent?) {
        task(ctx.inject().with("ConnectivityReceiver").instance()) {
            // Do it async so that Android can refresh the current network info before we access it
            v("connectivity receiver ping")
            device.connected.refresh()
            device.onWifi.refresh()
        }
    }

    companion object {
        fun register(ctx: Context) {
            val filter = IntentFilter()
            filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION)
            filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            ctx.registerReceiver(ctx.inject().instance<ConnectivityReceiver>(), filter)
        }

    }

}

fun newDeviceModule(ctx: Context): Kodein.Module {
    return Kodein.Module {
        bind<ConnectivityReceiver>() with singleton { ConnectivityReceiver() }
        bind<ScreenOnReceiver>() with singleton { ScreenOnReceiver() }
        bind<LocaleReceiver>() with singleton { LocaleReceiver() }
        onReady {
            // Register various Android listeners to receive events
            task {
                // In a task because we are in DI and using DI can lead to stack overflow
                ConnectivityReceiver.register(ctx)
                ScreenOnReceiver.register(ctx)
                LocaleReceiver.register(ctx)
            }
        }
    }
}

class ScreenOnReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent?) {
        task(ctx.inject().with("ScreenOnReceiver").instance()) {
            // This causes everything to load
            v("screen receiver ping")
            device.screenOn.refresh()
        }
    }

    companion object {
        fun register(ctx: Context) {
            // Register ScreenOnReceiver
            val filter = IntentFilter()
            filter.addAction(Intent.ACTION_SCREEN_ON)
            filter.addAction(Intent.ACTION_SCREEN_OFF)
            ctx.registerReceiver(ctx.inject().instance<ScreenOnReceiver>(), filter)
        }

    }
}

class LocaleReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent?) {
        task(ctx.inject().with("LocaleReceiver").instance()) {
            v("locale receiver ping")
            val i18n: I18n = ctx.inject().instance()
            i18n.locale.refresh(force = true)
        }
    }

    companion object {
        fun register(ctx: Context) {
            val filter = IntentFilter()
            filter.addAction(Intent.ACTION_LOCALE_CHANGED)
            ctx.registerReceiver(ctx.inject().instance<LocaleReceiver>(), filter)
        }

    }
}

/**
 * Watchdog is meant to test if device has Internet connectivity at this moment.
 *
 * It's used for getting connectivity state since Android's connectivity event cannot always be fully
 * trusted. It's also used to test if Blokada is working properly once activated (and periodically).
 */
class Watchdog {

    fun test(): Boolean {
        if (!device.watchdogOn()) return true
        v("watchdog ping")
        val socket = Socket()
        socket.soTimeout = 3000
        return try {
            socket.connect(InetSocketAddress("cloudflare.com", 80), 3000);
            v("watchdog ping ok")
            true
        }
        catch (e: Exception) {
            v("watchdog ping fail")
            false
        } finally {
            try { socket.close() } catch (e: Exception) {}
        }
    }

    private val MAX = 120
    private var started = false
    private var wait = 1
    private var nextTask: Promise<*, *>? = null

    @Synchronized fun start() {
        if (started) return
        if (!device.watchdogOn()) { return }
        started = true
        wait = 1
        if (nextTask != null) Kovenant.cancel(nextTask!!, Exception("cancelled"))
        nextTask = tick()
    }

    @Synchronized fun stop() {
        started = false
        if (nextTask != null) Kovenant.cancel(nextTask!!, Exception("cancelled"))
        nextTask = null
    }

    private fun tick(): Promise<*, *> {
        return task(kctxWatchdog) {
            if (started) {
                // Delay the first check to not cause false positives
                if (wait == 1) Thread.sleep(1000L)
                val connected = test()
                val next = if (connected) wait * 2 else wait
                wait *= 2
                if (device.connected() != connected) {
                    // Connection state change will cause reactivating (and restarting watchdog)
                    v("watchdog change: connected: $connected")
                    device.connected %= connected
                    stop()
                } else {
                    Thread.sleep(Math.min(next, MAX) * 1000L)
                    nextTask = tick()
                }
            }
        }
    }
}
