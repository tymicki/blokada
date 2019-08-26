package core

import android.app.Service
import android.content.Intent
import android.os.IBinder
import g11n.i18n
import kotlinx.coroutines.runBlocking
import org.blokada.R
import tunnel.Events
import tunnel.Request
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.util.*


class RequestLogWriter {

    private var file: PrintWriter? = try {
        val path = File(getExternalPath(), "requests.csv")
        val exists = path.exists()
        val writer = PrintWriter(FileOutputStream(path, true), true)
        if (!exists) {
            writer.println("timestamp,type,host")
        }
        writer
    } catch (ex: Exception) {
        null
    }

    @Synchronized
    internal fun writer(line: String) {
        Result.of { file!!.println(time() + ',' + line) }
    }

    private fun time() = Date().time.toString(10)
}

data class LoggerConfig(
        val active: Boolean = false,
        val logAllowed: Boolean = false,
        val logDenied: Boolean = false
): Persistable {
    override fun key() = "logger:config"
}

class RequestLogger : Service() {
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private var logger: RequestLogWriter? = null
    private var onAllowed = { r: Request -> if (!r.blocked) log(r.domain, false) }
    private var onBlocked = { r: Request -> if (r.blocked) log(r.domain, true) }
    var config = LoggerConfig()
        set(value) {
            if (field != value) {
                cancel(Events.REQUEST, onAllowed)
                cancel(Events.REQUEST, onBlocked)
                if (value.active) {
                    logger = RequestLogWriter()
                    if (value.logAllowed) {
                        core.on(Events.REQUEST, onAllowed)
                    }
                    if (value.logDenied) {
                        core.on(Events.REQUEST, onBlocked)
                    }
                } else {
                    stopSelf()
                }
                field = value
            }
        }

    fun log(host: String, blocked: Boolean) {
        logger?.writer(if (blocked) {
            'b'
        } else {
            'a'
        } + "," + host)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        v("logger service started")
        if (intent != null) {
            val newConfig: BooleanArray? = intent.getBooleanArrayExtra("config")

            if (newConfig != null) {
                if (newConfig.size == 3) {
                    config = LoggerConfig(active = newConfig[0], logAllowed = newConfig[1], logDenied = newConfig[2])
                }
            } else {
                if (intent.getBooleanExtra("load_on_start", false)) {
                    config = runBlocking { config.loadFromPersistence() }
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        cancel(Events.REQUEST, onAllowed)
        cancel(Events.REQUEST, onBlocked)
        super.onDestroy()
    }
}

class LoggerVB (
        onTap: (SlotView) -> Unit
): SlotVB(onTap) {

    override fun attach(view: SlotView) {
        view.type = Slot.Type.INFO
        view.enableAlternativeBackground()
        val config = runBlocking { LoggerConfig().loadFromPersistence() }
        view.apply {
            content = Slot.Content(
                    label = i18n.getString(R.string.logger_slot_title),
                    description = i18n.getString(R.string.logger_slot_desc),
                    values = listOf(
                            i18n.getString(R.string.logger_slot_mode_off),
                            i18n.getString(R.string.logger_slot_mode_denied),
                            i18n.getString(R.string.logger_slot_mode_allowed),
                            i18n.getString(R.string.logger_slot_mode_all)
                    ),
                    selected = configToMode(config)
            )
        }
        view.onSelect = {
            askForExternalStoragePermissionsIfNeeded()
            val newConfig = modeToConfig(it)
            runBlocking { newConfig.saveToPersistence() }
            sendConfigToService(newConfig)
        }
    }

    private fun configToMode(config: LoggerConfig) = i18n.getString(
            when {
                !config.active -> R.string.logger_slot_mode_off
                config.logAllowed && config.logDenied -> R.string.logger_slot_mode_all
                config.logDenied -> R.string.logger_slot_mode_denied
                else -> R.string.logger_slot_mode_allowed
    })

    private fun modeToConfig(mode: String) = when (mode) {
        i18n.getString(R.string.logger_slot_mode_off) -> LoggerConfig(active = false)
        i18n.getString(R.string.logger_slot_mode_allowed) -> LoggerConfig(active = true, logAllowed = true)
        i18n.getString(R.string.logger_slot_mode_denied) -> LoggerConfig(active = true, logDenied = true)
        else -> LoggerConfig(active = true, logAllowed = true, logDenied = true)
    }

    private fun sendConfigToService(config: LoggerConfig) {
        val ctx = runBlocking { getApplicationContext()!! }
        val serviceIntent = Intent(ctx.applicationContext, RequestLogger::class.java)
        val newConfigArray = BooleanArray(3)
        newConfigArray[0] = config.active
        newConfigArray[1] = config.logAllowed
        newConfigArray[2] = config.logDenied
        serviceIntent.putExtra("config", newConfigArray)
        ctx.startService(serviceIntent)
    }

    private fun askForExternalStoragePermissionsIfNeeded() {
        if (!checkStoragePermissions()) {
            runBlocking {
                getActivity()?.apply {
                    askStoragePermission(this)
                }
            }
        }
    }
}
