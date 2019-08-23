package gs.environment

import android.app.DownloadManager
import android.app.NotificationManager
import android.content.Context
import android.os.PowerManager
import com.github.salomonbrys.kodein.Kodein
import com.github.salomonbrys.kodein.bind
import com.github.salomonbrys.kodein.multiton
import com.github.salomonbrys.kodein.singleton
import nl.komponents.kovenant.android.androidUiDispatcher
import nl.komponents.kovenant.ui.KovenantUi

fun newGscoreModule(ctx: Context): Kodein.Module {
    return Kodein.Module {
        bind<Worker>() with multiton { it: String ->
            newSingleThreadedWorker(prefix = it)
        }
        bind<Worker>(2) with multiton { it: String ->
            newConcurrentWorker(prefix = it, tasks = 1)
        }
        bind<Worker>(3) with multiton { it: String ->
            newConcurrentWorker(prefix = it, tasks = 1)
        }
        bind<Worker>(10) with multiton { it: String ->
            newConcurrentWorker(prefix = it, tasks = 1)
        }

        bind<DownloadManager>() with singleton {
            ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        }
        bind<NotificationManager>() with singleton {
            ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        }
        bind<PowerManager>() with singleton {
            ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        }

        KovenantUi.uiContext {
            dispatcher = androidUiDispatcher()
        }
    }
}
