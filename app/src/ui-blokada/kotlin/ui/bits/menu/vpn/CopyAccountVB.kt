package ui.bits.menu.vpn

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import core.BitVB
import core.BitView
import core.getActivity
import core.res
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.blokada.R
import blocka.BLOCKA_CONFIG
import tunnel.BlockaConfig
import tunnel.showSnack

class CopyAccountVB : BitVB() {

    override fun attach(view: BitView) {
        view.alternative(true)
        view.icon(R.drawable.ic_blocked.res())
        view.label(R.string.slot_account_show.res())
        view.state("******".res())
        core.on(BLOCKA_CONFIG, update)
    }

    override fun detach(view: BitView) {
        core.cancel(BLOCKA_CONFIG, update)
    }

    private val update = { cfg: BlockaConfig? ->
        if (cfg != null)
        view?.apply {
            onTap {
                // Show
                icon(R.drawable.ic_show.res())
                state(cfg.accountId.res())

                // Copy
                val ctx = runBlocking { getActivity()!! }
                val clipboardManager = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clipData = ClipData.newPlainText("account-id", cfg.accountId)
                clipboardManager.primaryClip = clipData
                GlobalScope.launch { showSnack(R.string.slot_account_action_copied) }
            }
        }
        Unit
    }

}
