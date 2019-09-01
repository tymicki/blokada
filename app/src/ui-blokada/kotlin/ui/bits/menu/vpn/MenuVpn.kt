package ui.bits.menu.vpn

import android.content.Intent
import core.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.blokada.R
import ui.bits.VpnVB
import ui.bits.menu.MenuItemVB
import ui.bits.menu.MenuItemsVB
import ui.bits.menu.SimpleMenuItemVB
import ui.modalManager
import ui.pages

private fun createMenuVpn(): NamedViewBinder {
    return MenuItemsVB(
            items = listOf(
                LabelVB(label = R.string.menu_vpn_intro.res()),
                VpnVB(),
                createWhyVpnMenuItem(),
                LabelVB(label = R.string.menu_vpn_account_label.res()),
                createManageAccountMenuItem(),
                LabelVB(label = R.string.menu_vpn_gateways_label.res()),
                createGatewaysMenuItem(),
                LabelVB(label = R.string.slot_leases_info.res()),
                createLeasesMenuItem()
            ),
            name = R.string.menu_vpn.res()
    )
}

fun createVpnMenuItem(): NamedViewBinder {
    return MenuItemVB(
            label = R.string.menu_vpn.res(),
            icon = R.drawable.ic_shield_key_outline.res(),
            opens = createMenuVpn()
    )
}

fun createManageAccountMenuItem(): NamedViewBinder {
    return MenuItemVB(
            label = R.string.menu_vpn_account.res(),
            icon = R.drawable.ic_account_circle_black_24dp.res(),
            opens = createAccountMenu()
    )
}

fun createGatewaysMenuItem(): NamedViewBinder {
    return MenuItemVB(
            label = R.string.menu_vpn_gateways.res(),
            icon = R.drawable.ic_server.res(),
            opens = GatewaysDashboardSectionVB()
    )
}

fun createLeasesMenuItem(): NamedViewBinder {
    return MenuItemVB(
            label = R.string.menu_vpn_leases.res(),
            icon = R.drawable.ic_device.res(),
            opens = LeasesDashboardSectionVB()
    )
}

fun createWhyVpnMenuItem(): NamedViewBinder {
    val whyPage = pages.vpn
    return SimpleMenuItemVB(
            label = R.string.menu_vpn_intro_button.res(),
            icon = R.drawable.ic_help_outline.res(),
            action = {
                val ctx = runBlocking { getActivity()!! }
                GlobalScope.launch { modalManager.openModal() }
                ctx.startActivity(Intent(ctx, WebViewActivity::class.java).apply {
                    putExtra(WebViewActivity.EXTRA_URL, whyPage().toExternalForm())
                })
            }
    )
}

private fun createAccountMenu(): NamedViewBinder {
    return MenuItemsVB(
            items = listOf(
                    LabelVB(label = R.string.menu_vpn_manage_subscription.res()),
                    AccountVB(),
                    LabelVB(label = R.string.menu_vpn_account_secret.res()),
                    CopyAccountVB(),
                    LabelVB(label = R.string.menu_vpn_restore_label.res()),
                    RestoreAccountVB()
            ),
            name = R.string.menu_vpn_account.res()
    )
}
