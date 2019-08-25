package core.bits.menu.apps

import core.*
import core.bits.AppVB
import core.bits.SearchBarVB
import core.bits.menu.adblocking.SlotMutex
import gs.presentation.ListViewBinder
import gs.presentation.NamedViewBinder
import gs.property.IWhen
import org.blokada.R
import tunnel.Events
import tunnel.Filter

class AllAppsDashboardSectionVB(
        val system: Boolean,
        override val name: Resource = if (system) R.string.panel_section_apps_system.res() else R.string.panel_section_apps_all.res()
) : ListViewBinder(), NamedViewBinder {

    private val slotMutex = SlotMutex()

    private var apps: List<App> = emptyList()
    private var fil: Collection<String> = emptyList()
    private var filLoaded = false

    private var updateApps = { filters: Collection<Filter> ->
        fil = filters.filter { it.source.id == "app" && it.active }.map { it.source.source }
        filLoaded = true
        updateListing()
        Unit
    }

    private var getApps: IWhen? = null

    private fun updateListing(keyword: String = "") {
        if (apps.isEmpty() || !filLoaded) return

        val whitelisted = apps.filter { (it.appId in fil) && (keyword.isEmpty() || it.label.toLowerCase().contains(keyword.toLowerCase())) }.sortedBy { it.label.toLowerCase() }
        val notWhitelisted = apps.filter { (it.appId !in fil) && (keyword.isEmpty() || it.label.toLowerCase().contains(keyword.toLowerCase())) }.sortedBy { it.label.toLowerCase() }

        val listing = listOf(LabelVB(label = R.string.slot_allapp_whitelisted.res())) +
                whitelisted.map { AppVB(it, true,  onTap = slotMutex.openOneAtATime) } +
                LabelVB(label = R.string.slot_allapp_normal.res()) +
                notWhitelisted.map { AppVB(it, false, onTap = slotMutex.openOneAtATime) }
        view?.set(listing)
        view?.add(SearchBarVB(onSearch = { s ->
            updateListing(s)
        }), 0)
    }

    override fun attach(view: VBListView) {
        view.enableAlternativeMode()
        core.on(Events.FILTERS_CHANGED, updateApps)
        filtersManager.apps.refresh()
        getApps = filtersManager.apps.doOnUiWhenSet().then {
            apps = filtersManager.apps().filter { it.system == system }
            updateListing()
        }
    }

    override fun detach(view: VBListView) {
        slotMutex.detach()
        core.cancel(Events.FILTERS_CHANGED, updateApps)
        filtersManager.apps.cancel(getApps)
    }

}
