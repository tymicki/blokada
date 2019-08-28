package ui.bits.menu.dns

import core.*
import dns.dnsManager
import filter.filtersManager
import org.blokada.R
import ui.bits.*
import ui.bits.menu.adblocking.SlotMutex

class DnsDashboardSection(
        override val name: Resource = R.string.panel_section_advanced_dns.res()
) : ListViewBinder(), NamedViewBinder {

    private val slotMutex = SlotMutex()

    private var get: IWhen? = null

    override fun attach(view: VBListView) {
        view.enableAlternativeMode()
        filtersManager.apps.refresh()
        get = dnsManager.choices.doOnUiWhenSet().then {
            val default = dnsManager.choices().firstOrNull()
            val active = dnsManager.choices().filter { it.active }
            val inactive = dnsManager.choices().filter { !it.active }

            val defaultList = if (default != null) listOf(default) else emptyList()

            (defaultList + active.minus(defaultList) + inactive.minus(defaultList)).map {
                DnsChoiceVB(it, onTap = slotMutex.openOneAtATime)
            }.apply {
                view.set(this)
                view.add(LabelVB(label = R.string.menu_dns_intro.res()), 0)
                view.add(MenuActiveDnsVB(), 1)
                view.add(AddDnsVB(), 2)
                view.add(LabelVB(label = R.string.menu_dns_recommended.res()), 3)
                view.add(LabelVB(label = R.string.menu_manage.res()))
                view.add(DnsListControlVB(onTap = defaultOnTap))
                view.add(DnsFallbackVB(onTap = defaultOnTap))
            }
        }
    }

    override fun detach(view: VBListView) {
        slotMutex.detach()
        dnsManager.choices.cancel(get)
    }

}
