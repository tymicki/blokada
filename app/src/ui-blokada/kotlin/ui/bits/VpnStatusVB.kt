package ui.bits

import android.content.Intent
import blocka.BLOCKA_CONFIG
import core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.blokada.R
import tunnel.*
import ui.SubscriptionActivity
import ui.bits.menu.MENU_CLICK_BY_NAME
import ui.modalManager
import java.util.*

class VpnStatusVB(
) : ByteVB() {

    override fun attach(view: ByteView) {
        core.on(BLOCKA_CONFIG, configListener)
        stateListener = tunnelState.enabled.doOnUiWhenChanged().then {
            update()
        }
        enabledStateActor.listeners.add(tunnelListener)
        update()
    }

    override fun detach(view: ByteView) {
        core.cancel(BLOCKA_CONFIG, configListener)
        enabledStateActor.listeners.remove(tunnelListener)
        tunnelState.enabled.cancel(stateListener)
    }

    private var wasActive = false
    private var active = false
    private var activating = false
    private var config: BlockaConfig? = null
    private val configListener = { cfg: BlockaConfig ->
        config = cfg
        update()
        activateVpnAutomatically(cfg)
        wasActive = cfg.activeUntil.after(Date())
        Unit
    }

    private var stateListener: IWhen? = null

    private val update = {
        GlobalScope.launch(Dispatchers.Main.immediate) {
            view?.run {
                var config = this@VpnStatusVB.config
                if (config == null) config = BlockaConfig() // TODO
                when {
                    !tunnelState.enabled() -> {
                        icon(R.drawable.ic_shield_outline.res())
                        arrow(null)
                        switch(false)
                        label(R.string.home_setup_vpn.res())
                        state(R.string.home_vpn_disabled.res())
                        onTap {
                            core.emit(MENU_CLICK_BY_NAME, R.string.menu_vpn.res())
                        }
                        onSwitch {
                            if (!tunnelStateManager.turnVpn(it)) {
                                core.emit(MENU_CLICK_BY_NAME, R.string.menu_vpn.res())
                            }
                        }
                    }
                    config.blockaVpn && (activating || !active) -> {
                        icon(R.drawable.ic_shield_outline.res())
                        arrow(null)
                        switch(null)
                        label(R.string.home_connecting_vpn.res())
                        state(R.string.home_please_wait.res())
                        onTap {}
                        onSwitch {}
                    }
                    !config.blockaVpn && config.activeUntil.after(Date()) -> {
                        icon(R.drawable.ic_shield_outline.res())
                        arrow(null)
                        switch(false)
                        label(R.string.home_setup_vpn.res())
                        state(R.string.home_account_active.res())
                        onTap {
                            core.emit(MENU_CLICK_BY_NAME, R.string.menu_vpn.res())
                        }
                        onSwitch {
                            if (!tunnelStateManager.turnVpn(it)) {
                                core.emit(MENU_CLICK_BY_NAME, R.string.menu_vpn.res())
                            }
                        }
                    }
                    !config.blockaVpn -> {
                        icon(R.drawable.ic_shield_outline.res())
                        arrow(null)
                        switch(false)
                        label(R.string.home_setup_vpn.res())
                        state(R.string.home_vpn_disabled.res())
                        onTap {
//                            if (config.activeUntil.before(Date())) {
//                                GlobalScope.launch { modalManager.openModal() }
//                                context.startActivity(Intent(context, SubscriptionActivity::class.java))
//                            } else {
                                core.emit(MENU_CLICK_BY_NAME, R.string.menu_vpn.res())
//                            }
                        }
                        onSwitch {
                            if (!tunnelStateManager.turnVpn(it)) {
                                GlobalScope.launch {
                                    delay(2000)
                                    modalManager.openModal()
                                    context.startActivity(Intent(context, SubscriptionActivity::class.java))
                                }
                            }
                        }
                    }
                    else -> {
                        icon(R.drawable.ic_verified.res(), color = R.color.switch_on.res())
                        arrow(null)
                        switch(true)
                        label(config.gatewayNiceName.res())
                        state(R.string.home_connected_vpn.res())
                        onTap {
                            core.emit(MENU_CLICK_BY_NAME, R.string.menu_vpn.res())
                        }
                        onSwitch {
                            if (!tunnelStateManager.turnVpn(it)) {
                                core.emit(MENU_CLICK_BY_NAME, R.string.menu_vpn.res())
                            }
                        }
                    }
                }
            }
            Unit
        }
    }

    private val tunnelListener = object : IEnabledStateActorListener {
        override fun startActivating() {
            activating = true
            active = false
            update()
        }

        override fun finishActivating() {
            activating = false
            active = true
            update()
        }

        override fun startDeactivating() {
            activating = true
            active = false
            update()
        }

        override fun finishDeactivating() {
            activating = false
            active = false
            update()
        }
    }

    private fun activateVpnAutomatically(cfg: BlockaConfig) {
        if (!cfg.blockaVpn && !wasActive && cfg.activeUntil.after(Date()) && cfg.hasGateway()) {
            v("automatically enabling vpn on new subscription")
            core.emit(BLOCKA_CONFIG, cfg.copy(blockaVpn = true))
        }
    }

}
