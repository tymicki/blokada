package ui

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import tunnel.tunnelState
import org.blokada.R


class ListWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context?, appWidgetManager: AppWidgetManager?, appWidgetIds: IntArray?) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        val remoteViews = RemoteViews(context!!.packageName,
                R.layout.view_list_widget)

        var domainList = ""
        val duplicates = ArrayList<String>(0)
        tunnelState.tunnelRecentDropped().asReversed().forEach { s ->
            if (!duplicates.contains(s)) {
                duplicates.add(s)
                domainList += s + '\n'
            }
        }
        remoteViews.setTextViewText(R.id.widget_list_message, domainList)

        val intent = Intent(context, ANotificationsToggleService::class.java)
        intent.putExtra("new_state", !tunnelState.enabled())
        remoteViews.setOnClickPendingIntent(R.id.widget_list_button, PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT))
        if (tunnelState.enabled()) {
            remoteViews.setInt(R.id.widget_list_icon, "setColorFilter", color(context, active = true, waiting = false))
            remoteViews.setTextViewText(R.id.widget_list_button, context.resources.getString(R.string.notification_keepalive_deactivate))
        } else {
            remoteViews.setInt(R.id.widget_list_icon, "setColorFilter", color(context, active = false, waiting = false))
            remoteViews.setTextViewText(R.id.widget_list_button, context.resources.getString(R.string.notification_keepalive_activate))
        }
        appWidgetManager?.updateAppWidget(appWidgetIds, remoteViews)
    }

    private fun color(ctx: Context, active: Boolean, waiting: Boolean): Int {
        return when {
            waiting -> ctx.resources.getColor(R.color.colorLogoWaiting)
            active -> ctx.resources.getColor(android.R.color.transparent)
            else -> ctx.resources.getColor(R.color.colorLogoInactive)
        }
    }
}
