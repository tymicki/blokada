package core.bits.menu.vpn

import android.os.Handler
import core.*
import core.bits.menu.adblocking.SlotMutex
import gs.presentation.ListViewBinder
import gs.presentation.NamedViewBinder
import gs.presentation.ViewBinder
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import org.blokada.R
import retrofit2.Call
import retrofit2.Response
import tunnel.MAX_RETRIES
import tunnel.RestModel
import tunnel.restApi

class GatewaysDashboardSectionVB(
        val ktx: AndroidKontext,
        override val name: Resource = R.string.menu_vpn_gateways.res()
) : ListViewBinder(), NamedViewBinder {

    private val slotMutex = SlotMutex()

    private var items = listOf<ViewBinder>(
            LabelVB(ktx, label = R.string.menu_vpn_gateways_label.res())
    )

    private val gatewaysRequest = Handler {
        GlobalScope.async { populateGateways() }
        true
    }

    override fun attach(view: VBListView) {
        view.enableAlternativeMode()
        view.set(items)
        gatewaysRequest.sendEmptyMessage(0)
    }

    override fun detach(view: VBListView) {
        slotMutex.detach()
        gatewaysRequest.removeMessages(0)
    }

    private fun populateGateways(retry: Int = 0) {
        restApi.getGateways().enqueue(object : retrofit2.Callback<RestModel.Gateways> {
            override fun onFailure(call: Call<RestModel.Gateways>?, t: Throwable?) {
                e("gateways api call error", t ?: "null")
                if (retry < MAX_RETRIES) populateGateways(retry + 1)
                else gatewaysRequest.sendEmptyMessageDelayed(0, 5 * 1000)
            }

            override fun onResponse(call: Call<RestModel.Gateways>?, response: Response<RestModel.Gateways>?) {
                response?.run {
                    when (code()) {
                        200 -> {
                            body()?.run {
                                val g = gateways.map {
                                    GatewayVB(ktx, it, onTap = slotMutex.openOneAtATime)
                                }
                                items = listOf(
                                    LabelVB(ktx, label = R.string.menu_vpn_gateways_label.res())
                                ) + g
                                view?.set(items)
                            }
                        }
                        else -> {
                            e("gateways api call response ${code()}")
                            if (retry < MAX_RETRIES) populateGateways(retry + 1)
                            else gatewaysRequest.sendEmptyMessageDelayed(0, 30 * 1000)
                            Unit
                        }
                    }
                }
            }
        })
    }
}
