package blocka

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import core.getMostRecent
import core.v
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async

class RenewLicenseReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, p1: Intent) {
        GlobalScope.async {
            v("recheck account / lease task executing")
            val config = getMostRecent(BLOCKA_CONFIG)!!
            checkAccountInfo(config)
        }
    }
}
