package gs.property

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.PowerManager
import core.getApplicationContext
import core.logCoroutineExceptions
import core.v
import g11n.i18n
import gs.environment.isConnected
import gs.environment.isTethering
import gs.environment.isWifi
import kotlinx.coroutines.*
import java.net.InetSocketAddress
import java.net.Socket

val device by lazy {
    runBlocking {
        DeviceImpl(getApplicationContext()!!)
    }
}

val watchdog by lazy {
    runBlocking {
        Watchdog()
    }
}

class DeviceImpl (
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

    val appInForeground = newProperty({ false })
    val screenOn = newProperty({ pm.isInteractive })
    val connected = newProperty(zeroValue = { true }, refresh = {
        // With watchdog off always returning true, we basically disable detection.
        // Because isConnected sometimes returns false when we are actually online.
        val c = isConnected(ctx) or watchdog.test()
        v("connected", c)
        c
    } )
    val tethering = newProperty({ isTethering(ctx)} )

    val watchdogOn = newPersistedProperty2("watchdogOn",
            { false })

    val onWifi = newProperty({ isWifi(ctx) } )

    val reports = newPersistedProperty2("reports",
            { true }
    )

}

class ConnectivityReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent?) {
        GlobalScope.launch {
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
            ctx.registerReceiver(connectivityReceiver, filter)
        }

    }

}

private val connectivityReceiver = ConnectivityReceiver()
private val screenOnReceiver = ScreenOnReceiver()
private val localeReceiver = LocaleReceiver()

suspend fun initDevice() = withContext(Dispatchers.Main.immediate) {
    val ctx = getApplicationContext()!!
    ConnectivityReceiver.register(ctx)
    ScreenOnReceiver.register(ctx)
    LocaleReceiver.register(ctx)
}

class ScreenOnReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent?) {
        GlobalScope.launch {
            // This causes everything to load
            // TODO: double care after refactor
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
            ctx.registerReceiver(screenOnReceiver, filter)
        }

    }
}

class LocaleReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent?) {
        GlobalScope.launch {
            v("locale receiver ping")
            i18n.locale.refresh(force = true)
        }
    }

    companion object {
        fun register(ctx: Context) {
            val filter = IntentFilter()
            filter.addAction(Intent.ACTION_LOCALE_CHANGED)
            ctx.registerReceiver(localeReceiver, filter)
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

    private val kctx = newSingleThreadContext("watchdog") + logCoroutineExceptions()

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
    private var nextTask: Job? = null

    @Synchronized fun start() {
        if (started) return
        if (!device.watchdogOn()) { return }
        started = true
        wait = 1
        if (nextTask != null) nextTask?.cancel()
        nextTask = tick()
    }

    @Synchronized fun stop() {
        started = false
        if (nextTask != null) nextTask?.cancel()
        nextTask = null
    }

    private fun tick(): Job {
        return GlobalScope.launch(kctx) {
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
