package core.bits.menu.adblocking

import core.*
import core.bits.*
import core.bits.menu.MenuItemVB
import core.bits.menu.MenuItemsVB
import core.ListViewBinder
import core.NamedViewBinder
import core.ViewBinder
import org.blokada.R
import tunnel.Events
import tunnel.Filter

internal class SlotMutex {

    private var openedView: SlotView? = null

    val openOneAtATime = { view: SlotView ->
        val opened = openedView
        when {
            opened == null || !opened.isUnfolded() -> {
                openedView = view
                view.unfold()
            }
            opened.isUnfolded() -> {
                opened.fold()
                view.onClose()
            }
        }
    }

    fun detach() {
        openedView = null
    }
}


class FiltersSectionVB(
        override val name: Resource = R.string.panel_section_ads_lists.res()
) : ListViewBinder(), NamedViewBinder {

    private val slotMutex = SlotMutex()

    private val filtersUpdated = { filters: Collection<Filter> ->
        val items = filters.filter {
            !it.whitelist && !it.hidden && it.source.id != "single"
        }
        val activeItems = items.filter { it.active }.sortedBy { it.priority }.map { FilterVB(it, onTap = slotMutex.openOneAtATime) }
        val inactiveItems = items.filter { !it.active }.sortedBy { it.priority }.map { FilterVB(it, onTap = slotMutex.openOneAtATime) }

        view?.set(listOf(
                NewFilterVB(nameResId = R.string.slot_new_filter_list),
                LabelVB(label = R.string.menu_host_list_intro.res())
        ) + activeItems + inactiveItems)
        onSelectedListener(null)
        Unit
    }

    override fun attach(view: VBListView) {
        view.enableAlternativeMode()
        core.on(Events.FILTERS_CHANGED, filtersUpdated)
    }

    override fun detach(view: VBListView) {
        slotMutex.detach()
        core.cancel(Events.FILTERS_CHANGED, filtersUpdated)
    }

}

fun createHostsListMenuItem(): NamedViewBinder {
    return MenuItemVB(
            label = R.string.panel_section_ads_lists.res(),
            icon = R.drawable.ic_block.res(),
            opens = FiltersSectionVB()
    )
}

fun createHostsListDownloadMenuItem(): NamedViewBinder {
    return MenuItemVB(
            label = R.string.menu_host_list_settings.res(),
            icon = R.drawable.ic_tune.res(),
            opens = createMenuHostsDownload()
    )
}

private fun createMenuHostsDownload(): NamedViewBinder {
    return MenuItemsVB(
            items = listOf(
                    LabelVB(label = R.string.menu_host_list_status.res()),
                    FiltersStatusVB(onTap = defaultOnTap),
                    LabelVB(label = R.string.menu_host_list_download.res()),
                    FiltersListControlVB(onTap = defaultOnTap),
                    ListDownloadFrequencyVB(onTap = defaultOnTap),
                    DownloadOnWifiVB(onTap = defaultOnTap),
                    DownloadListsVB(onTap = defaultOnTap)
            ),
            name = R.string.menu_host_list_settings.res()
    )
}


class StaticItemsListVB(
        private val items: List<ViewBinder>
) : ListViewBinder() {

    private val slotMutex = SlotMutex()

    init {
        items.filter { it is SlotVB }.forEach {
            (it as SlotVB).onTap = slotMutex.openOneAtATime
        }
    }

    override fun attach(view: VBListView) {
        view.enableAlternativeMode()
        view.set(items)
    }

    override fun detach(view: VBListView) {
        slotMutex.detach()
    }
}
