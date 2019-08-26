package ui.bits.menu

import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import blocka.blokadaUserAgent
import core.*
import kotlinx.coroutines.runBlocking
import org.blokada.BuildConfig
import org.blokada.R
import ui.bits.UpdateVB
import ui.bits.menu.adblocking.createAdblockingMenuItem
import ui.bits.menu.advanced.createAdvancedMenuItem
import ui.bits.menu.apps.createAppsMenuItem
import ui.bits.menu.dns.createDnsMenuItem
import ui.bits.menu.vpn.createVpnMenuItem
import ui.bits.openInBrowser
import ui.pages
import java.io.File

fun createMenu(): MenuItemsVB {
    return MenuItemsVB(
            items = listOf(
                    LabelVB(label = R.string.menu_configure.res()),
                    createAdblockingMenuItem(),
                    createVpnMenuItem(),
                    createDnsMenuItem(),
                    LabelVB(label = R.string.menu_exclude.res()),
                    createAppsMenuItem(),
                    LabelVB(label = R.string.menu_dive_in.res()),
                    createDonateMenuItem(),
                    createAdvancedMenuItem(),
                    createLearnMoreMenuItem(),
                    createAboutMenuItem()
            ),
            name = R.string.panel_section_menu.res()
    )
}

fun createLearnMoreMenuItem(): NamedViewBinder {
    return MenuItemVB(
            label = R.string.menu_learn_more.res(),
            icon = R.drawable.ic_help_outline.res(),
            opens = createLearnMoreMenu()
    )
}

fun createLearnMoreMenu(): MenuItemsVB {
    return MenuItemsVB(
            items = listOf(
                    LabelVB(label = R.string.menu_knowledge.res()),
                    createBlogMenuItem(),
                    createHelpMenuItem(),
                    LabelVB(label = R.string.menu_get_involved.res()),
                    createCtaMenuItem(),
                    createTelegramMenuItem()
            ),
            name = R.string.menu_learn_more.res()
    )
}

fun createHelpMenuItem(): NamedViewBinder {
    val helpPage = pages.help
    return SimpleMenuItemVB(
            label = R.string.panel_section_home_help.res(),
            icon = R.drawable.ic_help_outline.res(),
            action = { openInBrowser(helpPage()) }
    )
}

fun createCtaMenuItem(): NamedViewBinder {
    val page = pages.cta
    return SimpleMenuItemVB(
            label = R.string.main_cta.res(),
            icon = R.drawable.ic_feedback.res(),
            action = { openInBrowser(page()) }
    )
}

fun createDonateMenuItem(): NamedViewBinder {
    val page = pages.donate
    return SimpleMenuItemVB(
            label = R.string.slot_donate_action.res(),
            icon = R.drawable.ic_heart_box.res(),
            action = { openInBrowser(page()) }
    )
}

fun createTelegramMenuItem(): NamedViewBinder {
    val page = pages.chat
    return SimpleMenuItemVB(
            label = R.string.menu_telegram.res(),
            icon = R.drawable.ic_comment_multiple_outline.res(),
            action = { openInBrowser(page()) }
    )
}

fun createBlogMenuItem(): NamedViewBinder {
    val page = pages.news
    return SimpleMenuItemVB(
            label = R.string.main_blog_text.res(),
            icon = R.drawable.ic_earth.res(),
            action = { openInBrowser(page()) }
    )
}

fun createAboutMenuItem(): NamedViewBinder {
    return MenuItemVB(
            label = R.string.slot_about.res(),
            icon = R.drawable.ic_info.res(),
            opens = createAboutMenu()
    )
}

fun createAboutMenu(): MenuItemsVB {
    val ctx = runBlocking { getApplicationContext()!! }
    return MenuItemsVB(
            items = listOf(
                    LabelVB(label = BuildConfig.VERSION_NAME.toString().res()),
                    UpdateVB(onTap = defaultOnTap),
                    LabelVB(label = R.string.menu_share_log_label.res()),
                    createLogMenuItem(),
                    LabelVB(label = R.string.menu_other.res()),
                    createAppDetailsMenuItem(),
                    createChangelogMenuItem(),
                    createCreditsMenuItem(),
                    LabelVB(label = blokadaUserAgent(ctx).res())
            ),
            name = R.string.slot_about.res()
    )
}

fun createCreditsMenuItem(): NamedViewBinder {
    val page = pages.credits
    return SimpleMenuItemVB(
            label = R.string.main_credits.res(),
            icon = R.drawable.ic_earth.res(),
            action = { openInBrowser(page()) }
    )
}

fun createChangelogMenuItem(): NamedViewBinder {
    val page = pages.changelog
    return SimpleMenuItemVB(
            label = R.string.main_changelog.res(),
            icon = R.drawable.ic_code_tags.res(),
            action = { openInBrowser(page()) }
    )
}

fun createLogMenuItem(): NamedViewBinder {
    return SimpleMenuItemVB(
            label = R.string.main_log.res(),
            icon = R.drawable.ic_bug_report_black_24dp.res(),
            action = {
                //                    if (askForExternalStoragePermissionsIfNeeded(activity)) {
                val ctx = runBlocking { getApplicationContext()!! }
                val uri = File(ctx.filesDir, "/blokada.log")
                val openFileIntent = Intent(Intent.ACTION_SEND)
                openFileIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                openFileIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                openFileIntent.type = "plain/*"
                openFileIntent.putExtra(Intent.EXTRA_STREAM,
                        FileProvider.getUriForFile(ctx, "${ctx.packageName}.files",
                        uri))
                ctx.startActivity(openFileIntent)
//                    }
            }
    )
}

fun createAppDetailsMenuItem(): NamedViewBinder {
    return SimpleMenuItemVB(
            label = R.string.update_button_appinfo.res(),
            icon = R.drawable.ic_info.res(),
            action = {
                try {
                    val ctx = runBlocking { getApplicationContext()!! }
                    ctx.startActivity(newAppDetailsIntent(ctx.packageName))
                } catch (e: Exception) {
                }
            }
    )
}

fun newAppDetailsIntent(packageName: String): Intent {
    val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
    intent.data = Uri.parse("package:$packageName")
    return intent
}
