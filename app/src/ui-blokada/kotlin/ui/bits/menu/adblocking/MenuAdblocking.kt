package ui.bits.menu.adblocking

import core.LabelVB
import core.NamedViewBinder
import core.res
import org.blokada.R
import ui.bits.Adblocking2VB
import ui.bits.menu.MenuItemVB
import ui.bits.menu.MenuItemsVB

private fun createMenuAdblocking(): NamedViewBinder {
    return MenuItemsVB(
            items = listOf(
                Adblocking2VB(),
                LabelVB(label = R.string.menu_ads_lists_label.res()),
                createHostsListMenuItem(),
                createHostsListDownloadMenuItem(),
                LabelVB(label = R.string.menu_ads_rules_label.res()),
                createWhitelistMenuItem(),
                createBlacklistMenuItem(),
                LabelVB(label = R.string.menu_ads_log_label.res()),
                createHostsLogMenuItem()
            ),
            name = R.string.panel_section_ads.res()
    )
}

fun createAdblockingMenuItem(): NamedViewBinder {
    return MenuItemVB(
            label = R.string.panel_section_ads.res(),
            icon = R.drawable.ic_blocked.res(),
            opens = createMenuAdblocking()
    )
}
