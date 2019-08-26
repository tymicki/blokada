package core.bits.menu.advanced

import adblocker.LoggerVB
import core.LabelVB
import core.bits.*
import core.bits.menu.MenuItemVB
import core.bits.menu.MenuItemsVB
import core.defaultOnTap
import core.res
import core.NamedViewBinder
import org.blokada.R

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
