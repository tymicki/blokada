package ui.bits.menu.vpn

import android.os.Handler
import blocka.MAX_RETRIES
import blocka.RestModel
import blocka.restApi
import core.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import org.blokada.R
import retrofit2.Call
import retrofit2.Response
import tunnel.BlockaConfig
import ui.bits.menu.adblocking.SlotMutex

class LeasesDashboardSectionVB(
        override val name: Resource = R.string.menu_vpn_leases.res()
) : ListViewBinder(), NamedViewBinder {

    private val slotMutex = SlotMutex()

    private var items = listOf<ViewBinder>(
            LabelVB(label = R.string.slot_leases_info.res())
    )

    private val request = Handler {
        GlobalScope.async { populate() }
        true
    }

    override fun attach(view: VBListView) {
        view.enableAlternativeMode()
        view.set(items)
        request.sendEmptyMessage(0)
    }

    override fun detach(view: VBListView) {
        slotMutex.detach()
        request.removeMessages(0)
    }

    private fun populate(retry: Int = 0) {
        val cfg = get(BlockaConfig::class.java)
        restApi.getLeases(cfg.accountId).enqueue(object : retrofit2.Callback<RestModel.Leases> {
            override fun onFailure(call: Call<RestModel.Leases>?, t: Throwable?) {
                e("leases api call error", t ?: "null")
                if (retry < MAX_RETRIES) populate(retry + 1)
                else request.sendEmptyMessageDelayed(0, 5 * 1000)
            }

            override fun onResponse(call: Call<RestModel.Leases>?, response: Response<RestModel.Leases>?) {
                response?.run {
                    when (code()) {
                        200 -> {
                            body()?.run {
                                val g = leases.map {
                                    LeaseVB(it, onTap = slotMutex.openOneAtATime,
                                            onRemoved = {
                                                items = items - it
                                                view?.set(items)
                                                request.sendEmptyMessageDelayed(0, 2000)
                                            })
                                }
                                items = listOf(
                                    LabelVB(label = R.string.slot_leases_info.res())
                                ) + g
                                view?.set(items)
                            }
                        }
                        else -> {
                            e("leases api call response ${code()}")
                            if (retry < MAX_RETRIES) populate(retry + 1)
                            else request.sendEmptyMessageDelayed(0, 30 * 1000)
                            Unit
                        }
                    }
                }
            }
        })
    }
}
