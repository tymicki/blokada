package core

import android.content.Context
import com.github.salomonbrys.kodein.*
import gs.environment.Worker
import gs.property.IProperty
import gs.property.newPersistedProperty2
import gs.property.repo
import notification.displayNotificationForUpdate
import org.blokada.BuildConfig
import update.AUpdateDownloader
import update.UpdateCoordinator

abstract class Update {
    abstract val lastSeenUpdateMillis: IProperty<Long>
}

class UpdateImpl (
        w: Worker
) : Update() {

    override val lastSeenUpdateMillis = newPersistedProperty2(w, "lastSeenUpdate", { 0L })
}

fun newUpdateModule(ctx: Context): Kodein.Module {
    return Kodein.Module {
        bind<Update>() with singleton {
            UpdateImpl(w = with("gscore").instance())
        }
        bind<UpdateCoordinator>() with singleton {
            UpdateCoordinator(downloader = AUpdateDownloader(ctx = ctx))
        }
        onReady {
            val u: Update = instance()

            // Check for update periodically
            tunnelState.tunnelState.doWhen { tunnelState.tunnelState(TunnelState.ACTIVE) }.then {
                // This "pokes" the cache and refreshes if needed
                repo.content.refresh()
            }

            // Display an info message when update is available
            repo.content.doOnUiWhenSet().then {
                if (isUpdate(ctx, repo.content().newestVersionCode)) {
                    u.lastSeenUpdateMillis.refresh(force = true)
                }
            }

            // Display notifications for updates
            u.lastSeenUpdateMillis.doOnUiWhenSet().then {
                val content = repo.content()
                val last = u.lastSeenUpdateMillis()
                val cooldown = 86400 * 1000L

                if (isUpdate(ctx, content.newestVersionCode) && canShowNotification(last, cooldown)) {
                    displayNotificationForUpdate(ctx, content.newestVersionName)
                    u.lastSeenUpdateMillis %= gs.environment.time.now()
                }
            }


        }
    }
}

internal fun canShowNotification(last: Long, cooldownMillis: Long): Boolean {
    return last + cooldownMillis < gs.environment.time.now()
}

fun isUpdate(ctx: Context, code: Int): Boolean {
    return code > BuildConfig.VERSION_CODE
}
