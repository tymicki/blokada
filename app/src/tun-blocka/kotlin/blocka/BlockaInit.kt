package blocka

import android.text.format.DateUtils
import core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import tunnel.BlockaConfig

suspend fun initBlocka() = withContext(Dispatchers.Main.immediate) {
    v("loading boringtun")
    System.loadLibrary("boringtun")
    v("boringtun loaded")

    // TODO
    val config = get(BlockaConfig::class.java)
    checkAccountInfo(config)

    on(BLOCKA_CONFIG) { it.update(BlockaConfig::class.java) }

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
