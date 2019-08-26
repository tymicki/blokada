package core.bits.menu.adblocking

import core.LabelVB
import core.Resource
import core.VBListView
import core.bits.FilterVB
import core.bits.NewFilterVB
import core.bits.menu.MenuItemVB
import core.res
import core.ListViewBinder
import core.NamedViewBinder
import org.blokada.R
import tunnel.Events
import tunnel.Filter

class BlacklistDashboardSection(
        override val name: Resource = R.string.panel_section_ads_blacklist.res()
) : ListViewBinder(), NamedViewBinder {

    private val slotMutex = SlotMutex()

    private var updateApps = { filters: Collection<Filter> ->
        val items = filters.filter { !it.whitelist && !it.hidden && it.source.id == "single" }
        val active = items.filter { it.active }
        val inactive = items.filter { !it.active }

        (active + inactive).map {
            FilterVB(it, onTap = slotMutex.openOneAtATime)
        }.apply { view?.set(listOf(
                LabelVB(label = R.string.menu_host_blacklist.res()),
                NewFilterVB(),
                LabelVB(label = R.string.panel_section_ads_blacklist.res())
        ) + this) }
        Unit
    }

    override fun attach(view: VBListView) {
        view.enableAlternativeMode()
        core.on(Events.FILTERS_CHANGED, updateApps)
    }

    override fun detach(view: VBListView) {
        slotMutex.detach()
        core.cancel(Events.FILTERS_CHANGED, updateApps)
    }

}

fun createBlacklistMenuItem(): NamedViewBinder {
    return MenuItemVB(
            label = R.string.panel_section_ads_blacklist.res(),
            icon = R.drawable.ic_shield_outline.res(),
            opens = BlacklistDashboardSection()
    )
}
