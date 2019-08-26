package flavor

import core.ActiveWidgetProvider
import core.ForegroundStartService
import core.ListWidgetProvider
import core.LoggerConfig
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import core.getApplicationContext
import core.loadFromPersistence
import tunnel.tunnelState
import core.ui
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import core.displayNotification
import core.hideNotification

suspend fun initFlavor() = withContext(Dispatchers.Main.immediate) {
    val ctx = runBlocking { getApplicationContext()!! }

    // Display notifications for dropped
   tunnelState.tunnelRecentDropped.doOnUiWhenSet().then {
        if (tunnelState.tunnelRecentDropped().isEmpty()) hideNotification(ctx)
        else if (ui.notifications()) displayNotification(ctx, tunnelState.tunnelRecentDropped().last())
    }

   tunnelState.tunnelRecentDropped.doWhenChanged().then{
        updateListWidget(ctx)
    }
   tunnelState.enabled.doWhenChanged().then{
        updateListWidget(ctx)
    }
    updateListWidget(ctx)

    // Hide notification when disabled
    ui.notifications.doOnUiWhenSet().then {
        hideNotification(ctx)
    }

    val config = runBlocking { LoggerConfig().loadFromPersistence() }
    val wm: AppWidgetManager = AppWidgetManager.getInstance(ctx)
    val ids = wm.getAppWidgetIds(ComponentName(ctx, ActiveWidgetProvider::class.java))
    if(((ids != null) and (ids.isNotEmpty())) or config.active) {
        val serviceIntent = Intent(ctx.applicationContext,
                ForegroundStartService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(serviceIntent)
        } else {
            ctx.startService(serviceIntent)
        }
    }

    // Initialize default values for properties that need it (async)
   tunnelState.tunnelDropCount {}
}

fun updateListWidget(ctx: Context){
    val updateIntent = Intent(ctx.applicationContext, ListWidgetProvider::class.java)
    updateIntent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
    val widgetManager = AppWidgetManager.getInstance(ctx)
    val ids = widgetManager.getAppWidgetIds(ComponentName(ctx, ListWidgetProvider::class.java))
    updateIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
    ctx.sendBroadcast(updateIntent)
}
