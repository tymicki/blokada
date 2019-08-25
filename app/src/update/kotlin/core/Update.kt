package core

import android.content.Context
import gs.environment.Worker
import gs.property.kctx
import gs.property.newPersistedProperty2
import gs.property.repo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import notification.displayNotificationForUpdate
import org.blokada.BuildConfig
import update.AUpdateDownloader
import update.UpdateCoordinator

val updateManager by lazy {
    UpdateImpl(kctx)
}

class UpdateImpl (
        w: Worker
) {

    val lastSeenUpdateMillis = newPersistedProperty2(w, "lastSeenUpdate", { 0L })
}

val updateCoordinator = UpdateCoordinator(downloader = AUpdateDownloader())

suspend fun initUpdate() = withContext(Dispatchers.Main.immediate) {
    val ctx = getApplicationContext()!!

    // Check for update periodically
    tunnelState.tunnelState.doWhen { tunnelState.tunnelState(TunnelState.ACTIVE) }.then {
        // This "pokes" the cache and refreshes if needed
        repo.content.refresh()
    }

    // Display an info message when update is available
    repo.content.doOnUiWhenSet().then {
        if (isUpdate(ctx, repo.content().newestVersionCode)) {
            updateManager.lastSeenUpdateMillis.refresh(force = true)
        }
    }

    // Display notifications for updates
    updateManager.lastSeenUpdateMillis.doOnUiWhenSet().then {
        val content = repo.content()
        val last = updateManager.lastSeenUpdateMillis()
        val cooldown = 86400 * 1000L

        if (isUpdate(ctx, content.newestVersionCode) && canShowNotification(last, cooldown)) {
            displayNotificationForUpdate(ctx, content.newestVersionName)
            updateManager.lastSeenUpdateMillis %= gs.environment.time.now()
        }
    }

}

internal fun canShowNotification(last: Long, cooldownMillis: Long): Boolean {
    return last + cooldownMillis < gs.environment.time.now()
}

fun isUpdate(ctx: Context, code: Int): Boolean {
    return code > BuildConfig.VERSION_CODE
}
