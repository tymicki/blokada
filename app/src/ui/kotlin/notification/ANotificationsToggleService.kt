package notification

import android.app.IntentService
import android.content.Intent
import android.os.Handler
import tunnel.tunnelState
import org.blokada.R


class ANotificationsToggleService : IntentService("notificationsToggle") {
    private var mHandler: Handler = Handler()

    override fun onHandleIntent(intent: Intent) {
        tunnelState.enabled %= intent.getBooleanExtra("new_state", true)
        if(intent.getBooleanExtra("new_state", true)){
            mHandler.post(DisplayToastRunnable(this, this.resources.getString(R.string.notification_keepalive_activating)))
        }else{
            mHandler.post(DisplayToastRunnable(this, this.resources.getString(R.string.notification_keepalive_deactivating)))
        }
    }

}
