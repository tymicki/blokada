package ui

import android.app.IntentService
import android.content.Intent


/**
 * ANotificationsOffService turns off notifications once intent is sent to it.
 */
class ANotificationsOffService : IntentService("notifications") {

    override fun onHandleIntent(intent: Intent) {
        uiState.notifications %= false
    }

}
