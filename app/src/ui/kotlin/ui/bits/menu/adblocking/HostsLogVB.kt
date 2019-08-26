package ui.bits.menu.adblocking

import com.github.michaelbull.result.getOr
import core.*
import org.blokada.R
import tunnel.Events
import tunnel.Persistence
import tunnel.Request
import ui.bits.DomainBlockedVB
import ui.bits.DomainForwarderVB
import ui.bits.SearchBarVB
import ui.bits.menu.MenuItemVB

class HostsLogVB(
        override val name: Resource = R.string.panel_section_ads_log.res()
) : ListViewBinder(), NamedViewBinder {

    private val slotMutex = SlotMutex()

    private val items = mutableListOf<SlotVB>()
    private var nextBatch = 0
    private var firstItem: Request? = null
    private var listener: (SlotVB?) -> Unit = {}
    private var searchString: String = ""

    private val request = { it: Request ->
        if (it != firstItem) {
            if(searchString.isEmpty() || it.domain.contains(searchString.toLowerCase())) {
                val dash = requestToVB(it)
                items.add(0, dash)
                view?.add(dash, 3)
                firstItem = it
                onSelectedListener(null)
            }
        }
        Unit
    }

    override fun attach(view: VBListView) {
        view.enableAlternativeMode()
        if(view.getItemCount() == 0) {
            view.add(SearchBarVB(onSearch = { s ->
                searchString = s
                nextBatch = 0
                this.items.clear()
                view.set(emptyList())
                attach(view)
            }))
            view.add(ResetHostLogVB {
                nextBatch = 0
                Persistence.request.clear()
                this.items.clear()
                view.set(emptyList())
                attach(view)
            })
            view.add(LabelVB(label = R.string.menu_ads_live_label.res()))
        }
        if (items.isEmpty()) {
            var items = loadBatch(0)
            items += loadBatch(1)
            nextBatch = 2
            if (items.isEmpty()) {
                items += loadBatch(2)
                nextBatch = 3
            }
            firstItem = items.getOrNull(0)
            addBatch(items)
        } else {
            items.forEach { view.add(it) }
        }

        core.on(Events.REQUEST, request)
        view.onEndReached = loadMore
    }

    override fun detach(view: VBListView) {
        slotMutex.detach()
        view.onEndReached = {}
        searchString = ""
        items.clear()
        core.cancel(Events.REQUEST, request)
    }

    private val loadMore = {
        if (nextBatch < 3) addBatch(loadBatch(nextBatch++))
    }

    private fun loadBatch(batch: Int) = Persistence.request.load(batch).getOr { emptyList() }.filter { r ->
        if (searchString.isEmpty()) true
        else r.domain.contains(searchString.toLowerCase())
    }

    private fun addBatch(batch: List<Request>) {
        items.addAll(batch.distinct().map {
            val dash = requestToVB(it)
            view?.add(dash)
            dash
        })
        if (items.size < 20) loadMore()
    }

    private fun requestToVB(it: Request): SlotVB {
        return if (it.blocked)
            DomainBlockedVB(it.domain, it.time, alternative = true, onTap = slotMutex.openOneAtATime) else
            DomainForwarderVB(it.domain, it.time,  alternative = true, onTap = slotMutex.openOneAtATime)
    }
}

fun createHostsLogMenuItem(): NamedViewBinder {
    return MenuItemVB(
            label = R.string.panel_section_ads_log.res(),
            icon = R.drawable.ic_menu.res(),
            opens = HostsLogVB()
    )
}

class ResetHostLogVB(
        private val onReset: () -> Unit
) : BitVB() {

    override fun attach(view: BitView) {
        view.alternative(true)
        view.icon(R.drawable.ic_reload.res())
        view.label(R.string.menu_ads_clear_log.res())
        view.onTap(onReset)
    }
}


