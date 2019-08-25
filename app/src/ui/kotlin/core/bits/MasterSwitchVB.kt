package core.bits

import core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.blokada.R

class MasterSwitchVB() : ByteVB() {

    private var active = false
    private var activating = false

    override fun attach(view: ByteView) {
        enabledStateActor.listeners.add(tunnelListener)
        enabledStateActor.update()
        update()
    }

    override fun detach(view: ByteView) {
        enabledStateActor.listeners.remove(tunnelListener)
    }

    private val update = {
        GlobalScope.launch(Dispatchers.Main.immediate) {
            view?.run {
                when {
                    !tunnelState.enabled() -> {
                        icon(R.drawable.ic_play_arrow.res())
                        label(R.string.home_touch_to_turn_on.res())
                        state(R.string.home_blokada_disabled.res())
                        important(true)
                        onTap {
                            tunnelState.enabled %= true
                        }
                    }
                    else -> {
                        icon(R.drawable.ic_pause.res())
                        label(R.string.home_masterswitch_on.res())
                        state(R.string.home_masterswitch_enabled.res())
                        important(false)
                        onTap {
                            tunnelState.enabled %= false
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

}
