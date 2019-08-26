package core.bits.menu.vpn

import android.content.Intent
import core.*
import core.bits.VpnVB
import core.bits.menu.MenuItemVB
import core.bits.menu.MenuItemsVB
import core.bits.menu.SimpleMenuItemVB
import core.NamedViewBinder
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.blokada.R

private fun createMenuVpn(): NamedViewBinder {
    return MenuItemsVB(
            items = listOf(
                LabelVB(label = R.string.menu_vpn_intro.res()),
                VpnVB(),
                createWhyVpnMenuItem(),
                LabelVB(label = R.string.menu_vpn_account_label.res()),
                createManageAccountMenuItem(),
                LabelVB(label = R.string.menu_vpn_gateways_label.res()),
                createGatewaysMenuItem()
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
