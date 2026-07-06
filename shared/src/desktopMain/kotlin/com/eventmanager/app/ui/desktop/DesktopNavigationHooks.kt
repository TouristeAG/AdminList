package com.eventmanager.app.ui.desktop

/** Desktop shell → shared UI navigation hooks (keyboard shortcuts, menu bar). */
object DesktopNavigationHooks {
    var openSettingsTab: (() -> Unit)? = null
    var focusListSearch: (() -> Unit)? = null
    var dismissOverlay: (() -> Unit)? = null
    /** `true` = next tab, `false` = previous tab (wraps at ends). */
    var cycleAdminTab: ((forward: Boolean) -> Unit)? = null
    var onQuit: (() -> Unit)? = null
}
