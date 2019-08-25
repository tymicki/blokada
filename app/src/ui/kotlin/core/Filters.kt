package core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import filter.DefaultHostlineProcessor
import gs.environment.Worker
import gs.property.kctx
import gs.property.newProperty
import kotlinx.coroutines.*
import org.blokada.R
import tunnel.FilterSourceDescriptor
import tunnel.tunnelManager

val filtersManager by lazy {
    FiltersImpl(kctx)
}

class FiltersImpl(
        private val kctx: Worker
) {

    val changed = newProperty(kctx, { false })

    private val appsRefresh = {
        v("apps refresh start")

        val ctx = runBlocking { getApplicationContext()!! }
        val installed = ctx.packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { it.packageName != ctx.packageName }
        val a = installed.map {
            App(
                    appId = it.packageName,
                    label = ctx.packageManager.getApplicationLabel(it).toString(),
                    system = (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            )
        }.sortedBy { it.label }
        v("found ${a.size} apps")
        a
    }

    val apps = newProperty(kctx, zeroValue = { emptyList<App>() }, refresh = { appsRefresh() },
            shouldRefresh = { it.isEmpty() })

}

private val appInstallReceiver by lazy { AppInstallReceiver() }
val hostlineProcessor = DefaultHostlineProcessor()

suspend fun initFilters() = withContext(Dispatchers.Main.immediate) {
    val ctx = runBlocking { getApplicationContext()!! }

    // Compile filters every time they change
    filtersManager.changed.doWhenChanged(withInit = true).then {
        if (filtersManager.changed()) {
            tunnelManager.sync(ctx.ktx("filters:sync:after:change"))
            filtersManager.changed %= false
        }
    }

    AppInstallReceiver.register(ctx)
}

data class App(
        val appId: String,
        val label: String,
        val system: Boolean
)

class AppInstallReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent?) {
        GlobalScope.launch {
            v("app install receiver ping")
            filtersManager.apps.refresh(force = true)
        }
    }

    companion object {
        fun register(ctx: Context) {
            val filter = IntentFilter()
            filter.addAction(Intent.ACTION_PACKAGE_ADDED)
            filter.addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
            filter.addDataScheme("package")
            ctx.registerReceiver(appInstallReceiver, filter)
        }

    }

}

internal fun id(name: String, whitelist: Boolean): String {
    return if (whitelist) "${name}_wl" else name
}

internal fun sourceToName(ctx: android.content.Context, source: FilterSourceDescriptor): String {
    val name = when (source.id) {
        "link" -> {
            ctx.getString(R.string.filter_name_link, source.source)
        }
        "file" -> {
            val source = try {
                Uri.parse(source.source)
            } catch (e: Exception) {
                null
            }
            ctx.getString(R.string.filter_name_file, source?.lastPathSegment
                    ?: ctx.getString(R.string.filter_name_file_unknown))
        }
        "app" -> {
            try {
                ctx.packageManager.getApplicationLabel(
                        ctx.packageManager.getApplicationInfo(source.source, PackageManager.GET_META_DATA)
                ).toString()
            } catch (e: Exception) {
                source.source
            }
        }
        else -> null
    }

    return name ?: source.source
}

internal fun sourceToIcon(ctx: android.content.Context, source: String): Drawable? {
    return try {
        ctx.packageManager.getApplicationIcon(
                ctx.packageManager.getApplicationInfo(source, PackageManager.GET_META_DATA)
        )
    } catch (e: Exception) {
        null
    }
}
