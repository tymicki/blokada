package ui.bits.menu.vpn

import android.os.Build
import blocka.BLOCKA_CONFIG
import blocka.RestModel
import blocka.deleteLease
import core.Slot
import core.SlotVB
import core.SlotView
import core.getActivity
import g11n.i18n
import kotlinx.coroutines.runBlocking
import org.blokada.R
import tunnel.BlockaConfig

class LeaseVB(
        private val lease: RestModel.LeaseInfo,
        val onRemoved: (LeaseVB) -> Unit = {},
        onTap: (SlotView) -> Unit
) : SlotVB(onTap) {

    private fun update(cfg: BlockaConfig) {
        val ctx = runBlocking { getActivity()!! }
        val currentDevice = lease.publicKey == cfg.publicKey
        view?.apply {
            content = Slot.Content(
                    label = if (currentDevice)
                        i18n.getString(R.string.slot_lease_name_current, "%s-%s".format(
                                Build.MANUFACTURER, Build.DEVICE
                        ))
                        else lease.niceName(),
                    icon = ctx.getDrawable(R.drawable.ic_device),
                    description = if (currentDevice) {
                        i18n.getString(R.string.slot_lease_description_current, lease.publicKey)
                    } else {
                        i18n.getString(R.string.slot_lease_description, lease.publicKey)
                    },
                    action1 = if (currentDevice) null else ACTION_REMOVE
            )

            onRemove = {
                deleteLease(BlockaConfig(
                        accountId = cfg.accountId,
                        publicKey = lease.publicKey,
                        gatewayId = lease.gatewayId
                ))
                onRemoved(this@LeaseVB)
            }
        }
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
