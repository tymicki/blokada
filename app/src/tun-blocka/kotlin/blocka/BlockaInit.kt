package blocka

import android.text.format.DateUtils
import core.*
import kotlinx.coroutines.*
import tunnel.BlockaConfig

suspend fun initBlocka() = withContext(Dispatchers.Main.immediate) {
    setPersistor(BlockaConfig::class.java, PaperPersistor("blocka:config"))

    v("loading boringtun")
    System.loadLibrary("boringtun")
    v("boringtun loaded")

    // TODO
    val config = loadPersistence(BlockaConfig::class.java) ?: BlockaConfig()
    checkAccountInfo(config)

    on(BLOCKA_CONFIG) { runBlocking { it.savePersistence() } }

    device.screenOn.doOnUiWhenChanged().then {
        if (device.screenOn()) GlobalScope.async {
            getMostRecent(BLOCKA_CONFIG)?.run {
                if (!DateUtils.isToday(lastDaily)) {
                    v("daily check account")
                    checkAccountInfo(copy(lastDaily = System.currentTimeMillis()))
                }
            }
        }
    }
}
