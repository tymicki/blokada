package ui.bits.menu.dns

import core.NamedViewBinder
import core.res
import org.blokada.R
import ui.bits.menu.MenuItemVB

fun createDnsMenuItem(): NamedViewBinder {
    return MenuItemVB(
            label = R.string.panel_section_advanced_dns.res(),
            icon = R.drawable.ic_server.res(),
            opens = DnsDashboardSection()
    )
}
