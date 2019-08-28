package ui.bits.menu.vpn

import android.content.Intent
import core.BitVB
import core.BitView
import core.getActivity
import core.res
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.blokada.R
import ui.RestoreAccountActivity
import ui.modalManager

class RestoreAccountVB : BitVB() {

    override fun attach(view: BitView) {
        view.alternative(true)
        view.icon(R.drawable.ic_reload.res())
        view.label(R.string.slot_account_action_change_id.res())
        view.onTap {
            GlobalScope.launch { modalManager.openModal() }
            val ctx = runBlocking { getActivity()!! }
            ctx.startActivity(Intent(ctx, RestoreAccountActivity::class.java))
        }
    }
}

