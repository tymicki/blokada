package tunnel

val enabledStateActor = EnabledStateActor()

/**
 * Translates internal MainState changes into higher level events used by topbar and fab.
 */
class EnabledStateActor(
        val listeners: MutableList<IEnabledStateActorListener> = mutableListOf()
) {

    // Refs to ensure listeners live only as long as this class
    private val listener1: Any
    private val listener2: Any
    private val listener3: Any

    init {
        listener1 = tunnelState.enabled.doOnUiWhenChanged().then { update() }
        listener2 = tunnelState.active.doOnUiWhenChanged().then { update() }
        listener3 = tunnelState.tunnelState.doOnUiWhenChanged().then { update() }
        update()
    }

    fun update() {
        when {
           tunnelState.tunnelState(TunnelState.ACTIVATING) -> startActivating()
           tunnelState.tunnelState(TunnelState.DEACTIVATING) -> startDeactivating()
           tunnelState.tunnelState(TunnelState.ACTIVE) -> finishActivating()
           tunnelState.active() -> startActivating()
            else -> finishDeactivating()
        }
    }

    private fun startActivating() {
        try { listeners.forEach { it.startActivating() } } catch (e: Exception) {}
    }

    private fun finishActivating() {
        try { listeners.forEach { it.finishActivating() } } catch (e: Exception) {}
    }

    private fun startDeactivating() {
        try { listeners.forEach { it.startDeactivating() } } catch (e: Exception) {}
    }

    private fun finishDeactivating() {
        try { listeners.forEach { it.finishDeactivating() } } catch (e: Exception) {}
    }
}

interface IEnabledStateActorListener {
    fun startActivating()
    fun finishActivating()
    fun startDeactivating()
    fun finishDeactivating()
}
