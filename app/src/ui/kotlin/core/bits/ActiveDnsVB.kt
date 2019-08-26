package core.bits

import android.util.Base64
import core.*
import core.bits.menu.MENU_CLICK_BY_NAME
import g11n.i18n
import core.IWhen
import dns.dnsManager
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.blokada.R
import tunnel.showSnack
import java.nio.charset.Charset

class ActiveDnsVB(
        private val simple: Boolean = false
) : ByteVB() {

    private var dnsServersChanged: IWhen? = null
    private var dnsEnabledChanged: IWhen? = null

    override fun attach(view: ByteView) {
        dnsServersChanged = dnsManager.dnsServers.doOnUiWhenSet().then(update)
        dnsEnabledChanged = dnsManager.enabled.doOnUiWhenSet().then(update)
        update()
    }

    override fun detach(view: ByteView) {
        dnsManager.dnsServers.cancel(dnsServersChanged)
        dnsManager.enabled.cancel(dnsEnabledChanged)
    }

    private val update = {
        view?.run {
            val item = dnsManager.choices().firstOrNull { it.active }
            if (item != null) {
                val id = if (item.id.startsWith("custom-dns:")) Base64.decode(item.id.removePrefix("custom-dns:"), Base64.NO_WRAP).toString(Charset.defaultCharset()) else item.id
                val name = i18n.localisedOrNull("dns_${id}_name") ?: item.comment ?: id.capitalize()

                if (dnsManager.enabled() && dnsManager.hasCustomDnsSelected(checkEnabled = false)) {
                    setTexts(name)
                } else {
                    setTexts(null)
                }
            } else {
                setTexts(null)
            }

//            if (dns.enabled() && !dns.hasCustomDnsSelected()) {
//                Handler {
//                    core.emit(MENU_CLICK_BY_NAME, R.string.panel_section_advanced_dns.res())
//                    true
//                }.sendEmptyMessageDelayed(0, 300)
//            }

            onTap {
                core.emit(MENU_CLICK_BY_NAME, R.string.panel_section_advanced_dns.res())
            }
            switch(dnsManager.enabled())
            onSwitch { enabled ->
                when {
                    enabled && !dnsManager.hasCustomDnsSelected(checkEnabled = false) -> {
                        GlobalScope.launch { showSnack(R.string.menu_dns_select.res()) }
                        core.emit(MENU_CLICK_BY_NAME, R.string.panel_section_advanced_dns.res())
                        switch(false)
                    }
                    else -> {
                        dnsManager.enabled %= enabled
                    }
                }
            }

        }
        Unit
    }

    private fun ByteView.setTexts(name: String?) {
        when {
            simple && name == null -> {
                icon(R.drawable.ic_server.res(), color = R.color.switch_on.res())
                label(i18n.getString(R.string.slot_dns_name_disabled).res())
                state(null)
            }
            simple && name != null -> {
                icon(R.drawable.ic_server.res(), color = R.color.switch_on.res())
                label(i18n.getString(R.string.slot_dns_name).res())
                state(null)
            }
            name == null -> {
                icon(R.drawable.ic_server.res())
                label(R.string.home_dns_touch.res())
                state(R.string.slot_dns_name_disabled.res())
            }
            else -> {
                icon(R.drawable.ic_server.res(), color = R.color.switch_on.res())
                label(name.res())
                state(i18n.getString(R.string.slot_dns_name).res())
            }
        }
    }
}

class MenuActiveDnsVB() : BitVB() {

    private var dnsServersChanged: IWhen? = null
    private var dnsEnabledChanged: IWhen? = null

    override fun attach(view: BitView) {
        dnsServersChanged = dnsManager.dnsServers.doOnUiWhenSet().then(update)
        dnsEnabledChanged = dnsManager.enabled.doOnUiWhenSet().then(update)
        update()
    }

    override fun detach(view: BitView) {
        dnsManager.dnsServers.cancel(dnsServersChanged)
        dnsManager.enabled.cancel(dnsEnabledChanged)
    }

    private val update = {
        view?.run {
            val item = dnsManager.choices().firstOrNull() { it.active }
            if (item != null) {
                val id = if (item.id.startsWith("custom-dns:")) Base64.decode(item.id.removePrefix("custom-dns:"), Base64.NO_WRAP).toString(Charset.defaultCharset()) else item.id
                val name = i18n.localisedOrNull("dns_${id}_name") ?: item.comment ?: id.capitalize()

                if (dnsManager.enabled() && dnsManager.hasCustomDnsSelected(checkEnabled = false)) {
                    icon(R.drawable.ic_server.res(), color = R.color.switch_on.res())
                    label(i18n.getString(R.string.slot_dns_name, name).res())
                } else {
                    icon(R.drawable.ic_server.res())
                    label(R.string.slot_dns_name_disabled.res())
                }
            } else {
                icon(R.drawable.ic_server.res())
                label(R.string.slot_dns_name_disabled.res())
            }

            switch(dnsManager.enabled())
            onSwitch { enabled ->
                when {
                    enabled && !dnsManager.hasCustomDnsSelected(checkEnabled = false) -> {
                        GlobalScope.launch { showSnack(R.string.menu_dns_select.res()) }
                        core.emit(MENU_CLICK_BY_NAME, R.string.panel_section_advanced_dns.res())
                        switch(false)
                    }
                    else -> {
                        dnsManager.enabled %= enabled
                    }
                }
            }

        }
        Unit
    }
}
