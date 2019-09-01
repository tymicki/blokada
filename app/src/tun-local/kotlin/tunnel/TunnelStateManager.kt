package tunnel

import blocka.BLOCKA_CONFIG
import blocka.checkAccountInfo
import core.*
import dns.dnsManager
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.blokada.R
import java.util.*

/**
 * Automatically decides on the state of Tunnel.enabled flag based on the
 * state of adblocking, vpn, and DNS.
 */

val tunnelStateManager = TunnelStateManager()

class TunnelStateManager {

    private var latest: BlockaConfig? = null
        @Synchronized get
        @Synchronized set

    init {
        on(BLOCKA_CONFIG) {
            latest = it
            check(it)
        }

        dnsManager.enabled.doWhenChanged(withInit = true).then {
            if (latest != null) check(latest!!)
        }

       tunnelState.enabled.doWhenChanged(withInit = true).then {
           val latest = this@TunnelStateManager.latest
           if (latest != null)
            when {
                !tunnelState.enabled() -> {
                    // Save state before pausing
                    runBlocking {
                        Register.set(TunnelPause::class.java,
                            TunnelPause(
                                    vpn = latest.blockaVpn,
                                    adblocking = latest.adblocking,
                                    dns = dnsManager.enabled()
                            )
                        )
                    }

                    v("pausing features.")
                    emit(BLOCKA_CONFIG, latest.copy(adblocking = false, blockaVpn = false))
                    dnsManager.enabled %= false
                }
                else -> {
                    // Restore the state
                    val ctx = runBlocking { getApplicationContext()!! }
                    val pause = runBlocking { Register.get(TunnelPause::class.java)!! }

                    val vpn = pause.vpn && latest.hasGateway() && latest.leaseActiveUntil.after(Date())
                    var adblocking = pause.adblocking && Product.current(ctx) != Product.DNS
                    var dns = pause.dns

                    v("restoring features, is (vpn, adblocking, dns): $vpn $adblocking $dns")

                    if (!adblocking && !vpn && !dns) {
                        if (Product.current(ctx) != Product.DNS) {
                            v("all features disabled, activating adblocking")
                            adblocking = true
                        } else {
                            v("all features disabled, activating dns")
                            dns = true
                        }
                    }

                    dnsManager.enabled %= dns
                    emit(BLOCKA_CONFIG, latest.copy(adblocking = adblocking, blockaVpn = vpn))
                }
            }
        }
    }

    private fun check(it: BlockaConfig) {
        when {
            !tunnelState.enabled() -> Unit
            !it.adblocking && !it.blockaVpn && !dnsManager.enabled() -> {
                v("turning off because no features enabled")
               tunnelState.enabled %= false
            }
            !it.adblocking && it.blockaVpn && !it.hasGateway() -> {
                v("turning off everything because no gateway selected")
                emit(BLOCKA_CONFIG, it.copy(blockaVpn = false))
               tunnelState.enabled %= false
            }
            (it.adblocking || dnsManager.enabled()) && it.blockaVpn && !it.hasGateway() -> {
                v("turning off vpn because no gateway selected")
                emit(BLOCKA_CONFIG, it.copy(blockaVpn = false))
            }
        }
    }

    fun turnAdblocking(on: Boolean): Boolean {
        if (latest != null) emit(BLOCKA_CONFIG, latest!!.copy(adblocking = on))
        return true
    }

    fun turnVpn(on: Boolean): Boolean {
        val latest = this@TunnelStateManager.latest
        if (latest == null) return false
        return when {
            !on -> {
                emit(BLOCKA_CONFIG, latest.copy(blockaVpn = false))
                true
            }
            latest.activeUntil.before(Date()) -> {
                GlobalScope.launch { showSnack(R.string.menu_vpn_activate_account.res()) }
                emit(BLOCKA_CONFIG, latest.copy(blockaVpn = false))
                false
            }
            !latest.hasGateway() -> {
                GlobalScope.launch { showSnack(R.string.menu_vpn_select_gateway.res()) }
                emit(BLOCKA_CONFIG, latest.copy(blockaVpn = false))
                false
            }
            else -> {
                val cfg = latest.copy(blockaVpn = true)
                emit(BLOCKA_CONFIG, cfg)
                checkAccountInfo(cfg)
                true
            }
        }
    }
}
