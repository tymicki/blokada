package ui.bits.menu.vpn

import android.content.Intent
import core.BitVB
import core.BitView
import core.getActivity
import core.res
import g11n.i18n
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.blokada.R
import tunnel.BLOCKA_CONFIG
import tunnel.BlockaConfig
import ui.SubscriptionActivity
import ui.bits.pretty
import ui.modalManager
import java.util.*

class AccountVB : BitVB() {

    override fun attach(view: BitView) {
        view.alternative(true)
        view.icon(R.drawable.ic_account_circle_black_24dp.res())
        view.onTap {
            val ctx = runBlocking { getActivity()!! }
            GlobalScope.launch { modalManager.openModal() }
            ctx.startActivity(Intent(ctx, SubscriptionActivity::class.java))
        }
        update(null)
        core.on(BLOCKA_CONFIG, update)
    }

    override fun detach(view: BitView) {
        core.cancel(BLOCKA_CONFIG, update)
    }

    private val update = { cfg: BlockaConfig? ->
        view?.apply {
            val isActive = cfg?.activeUntil?.after(Date()) ?: false
            val accountLabel = if (isActive)
                i18n.getString(R.string.slot_account_label_active, cfg!!.activeUntil.pretty())
            else i18n.getString(R.string.slot_account_label_inactive)

            label(accountLabel.res())

            val stateLabel = if (isActive) R.string.slot_account_action_manage.res()
                else R.string.slot_account_action_manage_inactive.res()
            state(stateLabel)
        }
        Unit
    }
}
