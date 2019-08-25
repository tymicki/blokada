package core.bits.menu.dns

import core.bits.menu.MenuItemVB
import core.getActivity
import core.res
import gs.presentation.NamedViewBinder
import kotlinx.coroutines.runBlocking
import org.blokada.R

fun createDnsMenuItem(): NamedViewBinder {
    val ctx = runBlocking { getActivity()!! }
    return MenuItemVB(
            label = R.string.panel_section_advanced_dns.res(),
            icon = R.drawable.ic_server.res(),
            opens = DnsDashboardSection(ctx)
    )
}
