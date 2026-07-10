package com.eventmanager.app.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.eventmanager.app.data.sync.FileAppLogger
import com.eventmanager.app.data.sync.SettingsManager
import com.eventmanager.app.platform.createAppStorage
import com.eventmanager.app.platform.createPlatformContext
import com.eventmanager.app.platform.hardware.DesktopWebcamSupport
import com.eventmanager.app.ui.AppRoot
import com.eventmanager.app.ui.desktop.DesktopNavigationHooks
import com.eventmanager.app.ui.desktop.DesktopWindowAppearance
import com.eventmanager.app.ui.platform.bootstrapAppLocale

fun main() {
    DesktopWindowAppearance.initBeforeUiToolkit()
    DesktopWebcamSupport.ensureInitialized()
    application {
        val platformContext = createPlatformContext()
        FileAppLogger.init(platformContext, SettingsManager(createAppStorage(platformContext)))
        bootstrapAppLocale(SettingsManager(createAppStorage(platformContext)).getLanguage())
        val windowState = rememberWindowState(width = 1280.dp, height = 900.dp)

        Window(
            onCloseRequest = ::exitApplication,
            title = "NoctuList",
            state = windowState
        ) {
            DesktopWindowThemeEffect(platformContext)
            LaunchedEffect(Unit) {
                window.minimumSize = java.awt.Dimension(1024, 768)
                DesktopAppIcon.loadAwtIcon()?.let { window.iconImage = it }
            }
            MenuBar {
                Menu("File") {
                    Item("Preferences", shortcut = androidx.compose.ui.input.key.KeyShortcut(androidx.compose.ui.input.key.Key.Comma, meta = true)) {
                        DesktopNavigationHooks.openSettingsTab?.invoke()
                    }
                    Item("Quit", shortcut = androidx.compose.ui.input.key.KeyShortcut(androidx.compose.ui.input.key.Key.Q, meta = true), onClick = ::exitApplication)
                }
                Menu("Help") {
                    Item("About NoctuList", onClick = { })
                }
            }
            AppRoot(platformContext = platformContext)
        }
    }
}
