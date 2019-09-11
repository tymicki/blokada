package core

import blocka.BlockaVpnState
import blocka.blockaVpnMain
import com.github.salomonbrys.kodein.instance
import core.bits.menu.MENU_CLICK_BY_NAME
import gs.property.Device
import gs.property.Repo
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.newSingleThreadContext
import kotlinx.coroutines.experimental.runBlocking
import org.blokada.R
import tunnel.TunnelConfig
import tunnel.tunnelMain
import java.net.InetSocketAddress

private val context = newSingleThreadContext("entrypoint") + logCoroutineExceptions()

val entrypoint = runBlocking { async(context) { Entrypoint() }.await() }

private sealed class SingularCommand
private class SwitchTun(on: Boolean): SingularCommand()
private class SwitchVpn(on: Boolean): SingularCommand()
private class SwitchAdblocking(on: Boolean): SingularCommand()
private class SwitchDns(on: Boolean): SingularCommand()
private class ChangeTunnelConfig(cfg: TunnelConfig): SingularCommand()
private class ChangeDnsServes(servers: List<InetSocketAddress>): SingularCommand()
private class SwitchWifi(on: Boolean): SingularCommand()
private class Resync(forceBlocka: Boolean): SingularCommand()

class Entrypoint {

    private val ctx by lazy { getActiveContext()!! }
    private val di by lazy { ctx.ktx("entrypoint").di() }
    private val repo by lazy { di.instance<Repo>() }
    private val device by lazy { di.instance<Device>() }
    private val dns by lazy { di.instance<Dns>() }
    private val tunnelState by lazy { di.instance<Tunnel>() }

    private var syncRequests = 0
    private var syncBlocka = false
    private var forceSyncBlocka = false

    private fun requestSync(blocka: Boolean = false, force: Boolean = false) {
        syncRequests++
        v("sync request: " + syncRequests)
        syncBlocka = syncBlocka || blocka
        forceSyncBlocka = forceSyncBlocka || force
        async(context) {
            if (--syncRequests == 0) {
                v("syncing after recent changes")
                try {
                    if (forceSyncBlocka) blockaVpnMain.sync().await()
                    else if (syncBlocka) blockaVpnMain.syncIfNeeded().await()
                    tunnelMain.sync().await()
                } catch (ex: Exception) {
                    e("failed syncing after recent changes", ex)
                }
                syncBlocka = false
                forceSyncBlocka = false
            }
        }
    }

    fun onAppStarted() = async(context) {
        v("onAppStarted")
        if(tunnelState.enabled()) onEnableTun()
        blockaVpnMain.sync(showErrorToUser = false).await()
        tunnelMain.sync()
    }

    fun onEnableTun() = async(context) {
        v("onEnableTun")
        tunnelState.tunnelState %= TunnelState.ACTIVATING
        val config = get(TunnelConfig::class.java)
        if (dns.hasCustomDnsSelected()) dns.enabled %= true
        tunnelMain.setTunnelConfiguration(config.copy(tunnelEnabled = true))
//        blockaVpnMain.syncIfNeeded().await()
//        tunnelMain.sync().await()
        requestSync(blocka = true)
        // TODO: tunnel state...
        tunnelState.tunnelState %= TunnelState.ACTIVE
    }

    fun onDisableTun() = async(context) {
        v("onDisableTun")
        tunnelState.tunnelState %= TunnelState.DEACTIVATING
        val config = get(TunnelConfig::class.java)
        dns.enabled %= false
        tunnelMain.setTunnelConfiguration(config.copy(tunnelEnabled = false))
        requestSync()
        //tunnelMain.sync().await()
        tunnelState.tunnelState %= TunnelState.DEACTIVATED
    }

    fun onVpnSwitched(on: Boolean) = async(context) {
        v("onVpnSwitched")
        try {
            if (on) {
                blockaVpnMain.enable().await()
                requestSync(blocka = true, force = true)
                //blockaVpnMain.sync().await()
            }
            else if (shouldPause(blockaEnabled = on)) tunnelState.enabled %= false
            else {
                blockaVpnMain.disable().await()
                requestSync()
            }
        } catch (ex: Exception) {
            emit(MENU_CLICK_BY_NAME, R.string.menu_vpn.res())
        }
    }

    fun onSwitchAdblocking(adblocking: Boolean) = async(context) {
        v("onSwitchAdblocking")
        tunnelMain.setAdblocking(adblocking)
        if (shouldPause(adblocking = adblocking)) tunnelState.enabled %= false
        else requestSync()
    }

    fun onChangeTunnelConfig(tunnelConfig: TunnelConfig) = async(context) {
        v("onChangeTunnelConfig")
        tunnelMain.setTunnelConfiguration(tunnelConfig)
        requestSync()
    }

    fun onSwitchDnsEnabled(enabled: Boolean) = async(context) {
        v("onSwitchDnsEnabled")
        dns.enabled %= enabled
        tunnelMain.setNetworkConfiguration(dns.dnsServers(), device.onWifi())
        if (shouldPause(dnsEnabled = enabled)) tunnelState.enabled %= false
        else requestSync()
    }

    fun onDnsServersChanged(dnsServers: List<InetSocketAddress>) = async(context) {
        v("onDnsServersChanged")
        tunnelMain.setNetworkConfiguration(dnsServers, device.onWifi())
        requestSync()
    }

    fun onSwitchedWifi(onWifi: Boolean) = async(context) {
        v("onSwitchedWifi")
        tunnelMain.setNetworkConfiguration(dns.dnsServers(), onWifi)
        requestSync()
    }

    fun onAccountChanged() = async(context) {
        v("onAccountChanged")
        requestSync(blocka = true, force = true)
//        blockaVpnMain.sync().await()
//        tunnelMain.sync()
    }

    fun onGatewayDeselected() = async(context) {
        v("onGatewayDeselected")
        blockaVpnMain.disable()
        requestSync()
    }

    fun onGatewaySelected(gatewayId: String) = async(context) {
        v("onGatewaySelected")
        blockaVpnMain.setGatewayIfOk(gatewayId)
        requestSync()
    }

    private fun shouldPause(
            adblocking: Boolean = get(TunnelConfig::class.java).adblocking,
            blockaEnabled: Boolean = get(BlockaVpnState::class.java).enabled,
            dnsEnabled: Boolean = dns.enabled()
    ) = !dnsEnabled && !blockaEnabled && !adblocking

    fun onWentOnline() = async(context) {
        v("onWentOnline")
        repo.content.refresh()
        requestSync()
    }

    fun onFiltersChanged() = async(context) {
        v("onFiltersChanged")
        requestSync()
    }

    fun onSaveFilter(filters: List<tunnel.Filter>) = async(context) {
        v("onSaveFilter")
        tunnelMain.putFilters(filters)
        requestSync()
    }

    fun onSaveFilter(filter: tunnel.Filter) = async(context) {
        v("onSaveFilter")
        tunnelMain.putFilter(filter)
        requestSync()
    }

    fun onRemoveFilter(filter: tunnel.Filter) = async(context) {
        v("onRemoveFilter")
        tunnelMain.removeFilter(filter)
        requestSync()
    }

    fun onInvalidateFilters() = async(context) {
        v("onInvalidateFilters")
        tunnelMain.invalidateFilters()
        requestSync()
    }

    fun onDeleteAllFilters() = async(context) {
        v("onDeleteAllFilters")
        tunnelMain.deleteAllFilters()
        requestSync()
    }

    fun onSetFiltersUrl(url: String) = async(context) {
        v("onSetFiltersUrl")
        tunnelMain.setFiltersUrl(url)
        requestSync()
    }

}
