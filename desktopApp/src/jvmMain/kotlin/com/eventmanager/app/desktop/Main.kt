package com.eventmanager.app.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.eventmanager.app.data.utils.AppTimeZone
import com.eventmanager.app.data.sync.FileAppLogger
import com.eventmanager.app.data.sync.SettingsManager
import com.eventmanager.app.platform.createAppStorage
import com.eventmanager.app.platform.createPlatformContext
import com.eventmanager.app.platform.hardware.DesktopWebcamSupport
import com.eventmanager.app.ui.AppRoot
import com.eventmanager.app.ui.desktop.DesktopNavigationHooks
import com.eventmanager.app.ui.desktop.DesktopWindowAppearance
import com.eventmanager.app.ui.platform.bootstrapAppLocale

private val isMacOs: Boolean =
    System.getProperty("os.name").orEmpty().contains("mac", ignoreCase = true)

fun main() {
    AppTimeZone.installAsJvmDefault()
    DesktopWindowAppearance.initBeforeUiToolkit()
    DesktopWebcamSupport.ensureInitialized()
    application {
        val platformContext = createPlatformContext()
        com.eventmanager.app.data.security.SecureCredentialStoreHolder.init(
            com.eventmanager.app.data.security.createSecureCredentialStore(platformContext),
        )
        val settingsManager = SettingsManager(platformContext)
        FileAppLogger.init(platformContext, settingsManager)
        bootstrapAppLocale(settingsManager.getLanguage())
        runCatching {
            com.eventmanager.app.data.remote.FirebaseBootstrap.ensureInitialized(
                platformContext,
                com.eventmanager.app.data.remote.FirebaseOptionsReader.fromSettings(settingsManager),
            )
        }
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
            // Native-feeling system menu on macOS only; on Windows/Linux the in-window
            // File/Help bar is redundant with in-app navigation.
            if (isMacOs) {
                MenuBar {
                    Menu("File") {
                        Item(
                            "Preferences",
                            shortcut = androidx.compose.ui.input.key.KeyShortcut(
                                androidx.compose.ui.input.key.Key.Comma,
                                meta = true,
                            ),
                        ) {
                            DesktopNavigationHooks.openSettingsTab?.invoke()
                        }
                        Item(
                            "Quit",
                            shortcut = androidx.compose.ui.input.key.KeyShortcut(
                                androidx.compose.ui.input.key.Key.Q,
                                meta = true,
                            ),
                            onClick = ::exitApplication,
                        )
                    }
                    Menu("Help") {
                        Item("About NoctuList", onClick = { })
                    }
                }
            }
            AppRoot(platformContext = platformContext)
        }
    }
}
