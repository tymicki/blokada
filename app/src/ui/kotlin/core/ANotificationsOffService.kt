package core

import android.app.IntentService
import android.content.Intent
import core.ui


/**
 * ANotificationsOffService turns off notifications once intent is sent to it.
 */
class ANotificationsOffService : IntentService("notifications") {

    override fun onHandleIntent(intent: Intent) {
        ui.notifications %= false
    }

}
