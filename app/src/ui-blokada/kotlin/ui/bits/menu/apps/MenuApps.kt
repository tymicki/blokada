package ui.bits.menu.apps

import core.LabelVB
import core.NamedViewBinder
import core.res
import org.blokada.R
import ui.bits.menu.MenuItemVB
import ui.bits.menu.MenuItemsVB

fun createAppsMenuItem(): NamedViewBinder {
    return MenuItemVB(
            label = R.string.panel_section_apps.res(),
            icon = R.drawable.ic_apps.res(),
            opens = createMenuApps()
    )
}

private fun createMenuApps(): NamedViewBinder {
    return MenuItemsVB(
            items = listOf(
                    LabelVB(label = R.string.menu_apps_intro.res()),
                    createInstalledAppsMenuItem(),
                    LabelVB(label = R.string.menu_apps_system_label.res()),
                    createAllAppsMenuItem()
            ),
            name = R.string.panel_section_apps.res()
    )
}

private fun createInstalledAppsMenuItem(): NamedViewBinder {
    return MenuItemVB(
            label = R.string.panel_section_apps_all.res(),
            icon = R.drawable.ic_apps.res(),
            opens = AllAppsDashboardSectionVB(system = false))
}

private fun createAllAppsMenuItem(): NamedViewBinder {
    return MenuItemVB(
            label = R.string.panel_section_apps_system.res(),
            icon = R.drawable.ic_apps.res(),
            opens = AllAppsDashboardSectionVB(system = true))
}
