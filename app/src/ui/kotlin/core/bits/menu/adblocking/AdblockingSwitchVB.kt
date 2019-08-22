package core.bits.menu.adblocking

import core.AndroidKontext
import core.BitVB
import core.BitView
import core.res
import org.blokada.R
import tunnel.BLOCKA_CONFIG
import tunnel.BlockaConfig

class AdblockingSwitchVB(
        private val ktx: AndroidKontext
) : BitVB() {

    override fun attach(view: BitView) {
        view.label(R.string.slot_adblocking_label.res())
        view.icon(R.drawable.ic_blocked.res())
        view.alternative(true)
        core.on(BLOCKA_CONFIG, update)
    }

    override fun detach(view: BitView) {
        core.cancel(BLOCKA_CONFIG, update)
    }

    private val update = { cfg: BlockaConfig ->
        view?.run {
            switch(cfg.adblocking)
            onSwitch {
                core.emit(BLOCKA_CONFIG, cfg.copy(adblocking = !cfg.adblocking))
            }
        }
        Unit
    }

}
