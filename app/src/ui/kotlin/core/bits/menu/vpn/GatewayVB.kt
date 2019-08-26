package core.bits.menu.vpn

import android.content.Intent
import blocka.RestModel
import core.*
import g11n.i18n
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.blokada.R
import tunnel.*
import java.util.*

class GatewayVB(
        private val gateway: RestModel.GatewayInfo,
        private val modal: ModalManager = modalManager,
        onTap: (SlotView) -> Unit
) : SlotVB(onTap) {

    private fun update(cfg: BlockaConfig) {
        val ctx = runBlocking { getActivity()!! }
        view?.apply {
            content = Slot.Content(
                    label = i18n.getString(
                            (if (gateway.overloaded()) R.string.slot_gateway_label_overloaded
                            else R.string.slot_gateway_label),
                            gateway.niceName()),
                    icon = ctx.getDrawable(
                            if (gateway.overloaded()) R.drawable.ic_shield_outline
                            else R.drawable.ic_verified
                    ),
                    description = if (gateway.publicKey == cfg.gatewayId) {
                        i18n.getString(R.string.slot_gateway_description_current,
                                getLoad(gateway.resourceUsagePercent), gateway.ipv4, gateway.region,
                                cfg.activeUntil)
                    } else {
                        i18n.getString(R.string.slot_gateway_description,
                                getLoad(gateway.resourceUsagePercent), gateway.ipv4, gateway.region)
                    },
                    switched = gateway.publicKey == cfg.gatewayId
            )

            onSwitch = {
                when {
                    gateway.publicKey == cfg.gatewayId -> {
                        // Turn off VPN feature
                        clearConnectedGateway(cfg, showError = false)
                    }
                    cfg.activeUntil.before(Date()) -> {
                        GlobalScope.launch { modal.openModal() }
                        ctx.startActivity(Intent(ctx, SubscriptionActivity::class.java))
                    }
                    gateway.overloaded() -> {
                        GlobalScope.launch { showSnack(R.string.slot_gateway_overloaded) }
                        // Resend event to re-select same gateway
                        core.emit(BLOCKA_CONFIG, cfg)
                    }
                    else -> {
                        checkGateways(cfg.copy(
                                gatewayId = gateway.publicKey,
                                gatewayIp = gateway.ipv4,
                                gatewayPort = gateway.port,
                                gatewayNiceName = gateway.niceName()
                        ))
                    }
                }
            }
        }
    }

    private fun getLoad(usage: Int): String {
        return i18n.getString(when (usage) {
            in 0..50 -> R.string.slot_gateway_load_low
            else -> R.string.slot_gateway_load_high
        })
    }

    private val onConfig = { cfg: BlockaConfig ->
        update(cfg)
        Unit
    }

    override fun attach(view: SlotView) {
        view.enableAlternativeBackground()
        view.type = Slot.Type.INFO
        core.on(BLOCKA_CONFIG, onConfig)
    }

    override fun detach(view: SlotView) {
        core.cancel(BLOCKA_CONFIG, onConfig)
    }
}
