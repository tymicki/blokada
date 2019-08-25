package core.bits.menu.adblocking

import core.LabelVB
import core.Resource
import core.VBListView
import core.bits.FilterVB
import core.bits.NewFilterVB
import core.bits.menu.MenuItemVB
import core.res
import gs.presentation.ListViewBinder
import gs.presentation.NamedViewBinder
import org.blokada.R
import tunnel.Events
import tunnel.Filter

class WhitelistDashboardSectionVB(
        override val name: Resource = R.string.panel_section_ads_whitelist.res()
) : ListViewBinder(), NamedViewBinder {

    private val slotMutex = SlotMutex()

    private var updateApps = { filters: Collection<Filter> ->
        val items = filters.filter { it.whitelist && !it.hidden && it.source.id != "app" }
        val active = items.filter { it.active }
        val inactive = items.filter { !it.active }

        (active + inactive).map {
            FilterVB(it, onTap = slotMutex.openOneAtATime)
        }.apply { view?.set(listOf(
                LabelVB(label = R.string.menu_host_whitelist.res()),
                NewFilterVB(whitelist = true, nameResId = R.string.slot_new_filter_whitelist),
                LabelVB(label = R.string.panel_section_ads_whitelist.res())
        ) + this) }
        Unit
    }

    override fun attach(view: VBListView) {
        view.enableAlternativeMode()
        core.on(Events.FILTERS_CHANGED, updateApps)
    }
}

fun createWhitelistMenuItem(): NamedViewBinder {
    return MenuItemVB(
            label = R.string.panel_section_ads_whitelist.res(),
            icon = R.drawable.ic_verified.res(),
            opens = WhitelistDashboardSectionVB()
    )
}
