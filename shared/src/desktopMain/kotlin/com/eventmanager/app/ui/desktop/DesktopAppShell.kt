package com.eventmanager.app.ui.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import com.eventmanager.app.data.sync.SettingsManager
import com.eventmanager.app.platform.PlatformContext
import com.eventmanager.app.platform.createAppStorage
import com.eventmanager.app.ui.theme.EventManagerTheme
import com.eventmanager.app.ui.theme.ThemeMode
import com.eventmanager.app.ui.desktop.DesktopNavigationHooks

/**
 * Desktop shell: resizable window, keyboard shortcuts, wraps shared [AppRootContent].
 */
@Composable
fun DesktopAppShell(
    platformContext: PlatformContext,
    onThemeModeChanged: (String) -> Unit = {},
    content: @Composable () -> Unit
) {
    val settingsManager = remember(platformContext) { SettingsManager(createAppStorage(platformContext)) }
    var themeModeString by remember { mutableStateOf(settingsManager.getThemeMode()) }
    val windowState = rememberWindowState(width = 1280.dp, height = 900.dp)

    EventManagerTheme(themeMode = ThemeMode.fromString(themeModeString), platformContext = platformContext, settingsManager = settingsManager) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val mod = event.isMetaPressed || event.isCtrlPressed
                    when {
                        mod && event.key == Key.Comma -> {
                            DesktopNavigationHooks.openSettingsTab?.invoke()
                            true
                        }
                        mod && event.key == Key.F -> {
                            DesktopNavigationHooks.focusListSearch?.invoke()
                            true
                        }
                        event.key == Key.Escape -> {
                            DesktopNavigationHooks.dismissOverlay?.invoke()
                            true
                        }
                        else -> false
                    }
                },
            color = MaterialTheme.colorScheme.background
        ) {
            content()
        }
    }
}
