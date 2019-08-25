package core.bits.menu.adblocking

import core.LabelVB
import core.bits.Adblocking2VB
import core.bits.menu.MenuItemVB
import core.bits.menu.MenuItemsVB
import core.res
import gs.presentation.NamedViewBinder
import org.blokada.R

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
