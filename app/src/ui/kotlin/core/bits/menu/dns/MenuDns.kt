package core.bits.menu.dns

import core.bits.menu.MenuItemVB
import core.res
import core.NamedViewBinder
import org.blokada.R

fun createDnsMenuItem(): NamedViewBinder {
    return MenuItemVB(
            label = R.string.panel_section_advanced_dns.res(),
            icon = R.drawable.ic_server.res(),
            opens = DnsDashboardSection()
    )
}
