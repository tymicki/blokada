package core.bits

import android.content.Context
import android.content.Intent
import core.*
import core.bits.menu.isLandscape
import gs.presentation.ListViewBinder
import gs.presentation.NamedViewBinder
import gs.presentation.ViewBinder
import gs.property.version
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.blokada.BuildConfig
import org.blokada.R
import tunnel.BLOCKA_CONFIG
import tunnel.BlockaConfig
import java.util.*

data class SlotsSeenStatus(
        val intro: Boolean = false,
        val telegram: Boolean = false,
        val blog: Boolean = false,
        val updated: Int = 0,
        val cta: Int = 0,
        val donate: Int = 0
)

private const val PERSISTENCE_KEY = "slots:status"

class HomeDashboardSectionVB(
        override val name: Resource = R.string.panel_section_home.res()
) : ListViewBinder(), NamedViewBinder {

    override fun attach(view: VBListView) {
        core.on(BLOCKA_CONFIG, listener)
        if (isLandscape(view.context)) {
            view.enableLandscapeMode(reversed = false)
            view.set(items)
        } else view.set(items)
    }

    override fun detach(view: VBListView) {
        core.cancel(BLOCKA_CONFIG, listener)
    }

    private val update = {
        view?.run {
            val noSubscription = cfg.activeUntil.before(Date())
            val (slot, name) = decideOnSlot(noSubscription)
            if (slot != null && added == null) {
                items = listOf(slot) + items
                added = name
                if (slot is SimpleByteVB) slot.onTapped = {
                    // Remove this slot
                    markAsSeen()
                    items = items.subList(1, items.size)
                    set(items)
                }
            }
            set(items)
            if (isLandscape(context)) {
                enableLandscapeMode(reversed = false)
                set(items)
            }
        }
    }

    private var items = listOf<ViewBinder>(
            MasterSwitchVB(),
            AdsBlockedVB(),
            VpnStatusVB(),
            ActiveDnsVB(),
            ShareVB()
    )

    private val listener = { config: BlockaConfig ->
        cfg = config
        update()
        Unit
    }

    private var cfg: BlockaConfig = BlockaConfig()
    private var added: OneTimeByte? = null
    private val oneTimeBytes = createOneTimeBytes()

    private fun markAsSeen() {
        val cfg = loadPersistence(PERSISTENCE_KEY, { SlotsSeenStatus() })
        val newCfg = when (added) {
            OneTimeByte.UPDATED -> cfg.copy(updated = BuildConfig.VERSION_CODE)
            OneTimeByte.DONATE -> cfg.copy(donate = BuildConfig.VERSION_CODE)
            else -> cfg
        }
        savePersistence(PERSISTENCE_KEY, newCfg)
    }

    private fun decideOnSlot(noSubscription: Boolean): Pair<ViewBinder?, OneTimeByte?> {
        val cfg = loadPersistence(PERSISTENCE_KEY, { SlotsSeenStatus() })
        val name = if (cfg == null) null else when {
            //isLandscape(ktx.ctx) -> null
            BuildConfig.VERSION_CODE > cfg.updated -> OneTimeByte.UPDATED
            (BuildConfig.VERSION_CODE > cfg.donate) && noSubscription -> OneTimeByte.DONATE
            version.obsolete() -> OneTimeByte.OBSOLETE
            getInstalledBuilds().size > 1 -> OneTimeByte.CLEANUP
            else -> null
        }
        return oneTimeBytes[name] to name
    }

    private fun getInstalledBuilds(): List<String> {
        return welcome.conflictingBuilds().map {
            if (isPackageInstalled(it)) it else null
        }.filterNotNull()
    }

    private fun isPackageInstalled(appId: String): Boolean {
        val ctx = runBlocking { getApplicationContext()!! }
        val intent = ctx.packageManager.getLaunchIntentForPackage(appId) as Intent? ?: return false
        val activities = ctx.packageManager.queryIntentActivities(intent, 0)
        return activities.size > 0
    }
}

class VpnVB() : BitVB() {

    override fun attach(view: BitView) {
        core.on(BLOCKA_CONFIG, configListener)
        update()
    }

    override fun detach(view: BitView) {
        core.cancel(BLOCKA_CONFIG, configListener)
    }

    private var config: BlockaConfig = BlockaConfig()
    private val configListener = { cfg: BlockaConfig ->
        config = cfg
        update()
        Unit
    }

    private val update = {
        view?.apply {
            if (config.blockaVpn) {
                label(R.string.home_vpn_enabled.res())
                icon(R.drawable.ic_verified.res(), color = R.color.switch_on.res())
            } else {
                label(R.string.home_vpn_disabled.res())
                icon(R.drawable.ic_shield_outline.res())
            }
            switch(config.blockaVpn)
            onSwitch { turnOn ->
                tunnelStateManager.turnVpn(turnOn)
            }
        }
        Unit
    }
}

class Adblocking2VB() : BitVB() {

    override fun attach(view: BitView) {
        core.on(BLOCKA_CONFIG, configListener)
        update()
    }

    override fun detach(view: BitView) {
        core.cancel(BLOCKA_CONFIG, configListener)
    }

    private var config: BlockaConfig = BlockaConfig()
    private val configListener = { cfg: BlockaConfig ->
        config = cfg
        update()
        Unit
    }

    private val update = {
        view?.apply {
            if (config.adblocking) {
                label(R.string.home_adblocking_enabled.res())
                icon(R.drawable.ic_blocked.res(), color = R.color.switch_on.res())
            } else {
                label(R.string.home_adblocking_disabled.res())
                icon(R.drawable.ic_show.res())
            }
            switch(config.adblocking)
            onSwitch { adblocking ->
                if (!adblocking && !config.blockaVpn) tunnelState.enabled %= false
                core.emit(BLOCKA_CONFIG, config.copy(adblocking = adblocking))
            }
        }
        Unit
    }

}

class SimpleByteVB(
        private val label: Resource,
        private val description: Resource,
        private val onTap: () -> Unit,
        var onTapped: () -> Unit = {}
) : ByteVB() {
    override fun attach(view: ByteView) {
        view.icon(null)
        view.label(label)
        view.state(description, smallcap = false)
        view.onTap {
            onTap()
            onTapped()
        }
    }
}

enum class OneTimeByte {
    CLEANUP, UPDATED, OBSOLETE, DONATE
}

fun createOneTimeBytes() = mapOf(
        OneTimeByte.CLEANUP to CleanupVB(),
        OneTimeByte.UPDATED to SimpleByteVB(
                label = R.string.home_whats_new.res(),
                description = R.string.slot_updated_desc.res(),
                onTap = {
                    val ctx = runBlocking { getApplicationContext()!! }
                    GlobalScope.launch { modalManager.openModal() }
                    ctx.startActivity(Intent(ctx, WebViewActivity::class.java).apply {
                        putExtra(WebViewActivity.EXTRA_URL, pages.updated().toExternalForm())
                    })
                }
        ),
        OneTimeByte.OBSOLETE to SimpleByteVB(
                label = R.string.home_update_required.res(),
                description = R.string.slot_obsolete_desc.res(),
                onTap = {
                    openInBrowser(pages.download())
                }
        ),
        OneTimeByte.DONATE to SimpleByteVB(
                label = R.string.home_donate.res(),
                description = R.string.slot_donate_desc.res(),
                onTap = {
                    openInBrowser(pages.donate())
                }
        )
)


class ShareVB : ByteVB() {
    override fun attach(view: ByteView) {
        view.run {
            icon(null)
            arrow(R.drawable.ic_share.res())
            label(R.string.home_share.res())
            state(R.string.home_share_state.res())
            onArrowTap { share() }
            onTap { share() }
        }
    }

    private fun share() {
        try {
            val ctx = runBlocking { getApplicationContext()!! }
            val shareIntent: Intent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, getMessage(ctx,
                        tunnelState.tunnelDropStart(), Format.counter(tunnelState.tunnelDropCount())))
                type = "text/plain"
            }
            ctx.startActivity(Intent.createChooser(shareIntent,
                    ctx.getText(R.string.slot_dropped_share_title)))
        } catch (e: Exception) {}
    }

    private fun getMessage(ctx: Context, timeStamp: Long, dropCount: String): String {
        var elapsed: Long = System.currentTimeMillis() - timeStamp
        elapsed /= 60000
        if (elapsed < 120) {
            return ctx.resources.getString(R.string.social_share_body_minute, dropCount, elapsed)
        }
        elapsed /= 60
        if (elapsed < 48) {
            return ctx.resources.getString(R.string.social_share_body_hour, dropCount, elapsed)
        }
        elapsed /= 24
        if (elapsed < 28) {
            return ctx.resources.getString(R.string.social_share_body_day, dropCount, elapsed)
        }
        elapsed /= 7
        return ctx.resources.getString(R.string.social_share_body_week, dropCount, elapsed)
    }

}
