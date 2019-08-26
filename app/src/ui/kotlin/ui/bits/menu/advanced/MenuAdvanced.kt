package ui.bits.menu.advanced

import core.*
import org.blokada.R
import ui.bits.*
import ui.bits.menu.MenuItemVB
import ui.bits.menu.MenuItemsVB

private fun createMenuAdvanced(): NamedViewBinder {
    return MenuItemsVB(
            items = listOf(
                    LabelVB(label = R.string.label_basic.res()),
                    NotificationsVB(onTap = defaultOnTap),
                    StartOnBootVB(onTap = defaultOnTap),
                    StorageLocationVB(onTap = defaultOnTap),
                    LabelVB(label = R.string.label_advanced.res()),
                    BackgroundAnimationVB(onTap = defaultOnTap),
                    ResetCounterVB(onTap = defaultOnTap),
                    LoggerVB(onTap = defaultOnTap),
                    KeepAliveVB(onTap = defaultOnTap),
                    WatchdogVB(onTap = defaultOnTap),
                    PowersaveVB(onTap = defaultOnTap),
                    ReportVB(onTap = defaultOnTap)
            ),
            name = R.string.panel_section_advanced_settings.res()
    )
}

fun createAdvancedMenuItem(): NamedViewBinder {
    return MenuItemVB(
            label = R.string.panel_section_advanced_settings.res(),
            icon = R.drawable.ic_tune.res(),
            opens = createMenuAdvanced()
    )
}
