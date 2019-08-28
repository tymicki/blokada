package ui

import core.*
import g11n.g11Manager
import g11n.i18n
import kotlinx.coroutines.*
import org.blokada.BuildConfig
import org.blokada.R
import tunnel.tunnelManager

val uiState by lazy {
    runBlocking {
        AUiState()
    }
}

class AUiState {

    private val ctx by lazy {
        runBlocking { getApplicationContext()!! }
    }

    val seenWelcome = newPersistedProperty(APrefsPersistence(ctx, "seenWelcome"),
            { false }
    )

    val notifications = newPersistedProperty(APrefsPersistence(ctx, "notifications"),
            { false } // By default, have notifications off. 
    )

    val showSystemApps = newPersistedProperty(APrefsPersistence(ctx, "showSystemApps"),
            { true }
    )

    val showBgAnim = newPersistedProperty(APrefsPersistence(ctx, "backgroundAnimation"),
            { true }
    )
}

suspend fun initApp() = withContext(Dispatchers.Main.immediate) {
    val ctx = runBlocking { getApplicationContext()!! }
    GlobalScope.launch {
        g11Manager.load()

        core.on(tunnel.Events.FILTERS_CHANGED) {
            g11Manager.sync()
        }
    }

    i18n.locale.doWhenChanged().then {
        v("refresh filters on locale set")
        g11Manager.sync()
    }

    // Since having filters is really important, poke whenever we get connectivity
    var wasConnected = false
    device.connected.doWhenChanged().then {
        if (device.connected() && !wasConnected) {
            repo.content.refresh()
            tunnelManager.sync()
        }
        wasConnected = device.connected()
    }

    version.appName %= ctx.getString(R.string.branding_app_name)

    val p = BuildConfig.VERSION_NAME.split('.')
    version.name %= if (p.size == 3) "%s.%s (%s)".format(p[0], p[1], p[2])
    else BuildConfig.VERSION_NAME

    version.name %= version.name() + " " + BuildConfig.BUILD_TYPE.capitalize()

    // This will fetch repo unless already cached
    repo.url %= "https://blokada.org/api/v4/${BuildConfig.FLAVOR}/${BuildConfig.BUILD_TYPE}/repo.txt"
}

