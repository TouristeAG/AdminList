package com.eventmanager.app.ui.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.eventmanager.app.data.sync.SettingsManager
import com.eventmanager.app.platform.PlatformContext
import com.eventmanager.app.platform.createAppStorage
import com.eventmanager.app.ui.platform.AppAppearanceState
import com.eventmanager.app.ui.platform.AppLocaleEnvironment
import com.eventmanager.app.ui.theme.EventManagerTheme
import com.eventmanager.app.ui.theme.ThemeMode
import com.eventmanager.app.ui.platform.DesktopToastBus

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
    val appearanceNonce by AppAppearanceState::refreshNonce
    val appearanceLocale by AppAppearanceState::localeCode
    val appearanceTheme by AppAppearanceState::themeMode
    val themeRefreshNonce by AppAppearanceState::themeRefreshNonce

    var themeModeString by remember {
        mutableStateOf(appearanceTheme ?: settingsManager.getThemeMode())
    }
    var languageCode by remember {
        mutableStateOf(appearanceLocale ?: settingsManager.getLanguage())
    }

    LaunchedEffect(appearanceNonce, appearanceTheme, appearanceLocale, themeRefreshNonce) {
        themeModeString = appearanceTheme ?: settingsManager.getThemeMode()
        languageCode = appearanceLocale ?: settingsManager.getLanguage()
        onThemeModeChanged(themeModeString)
    }

    AppLocaleEnvironment(languageCode = languageCode, refreshNonce = appearanceNonce) {
        EventManagerTheme(
            themeMode = ThemeMode.fromString(themeModeString),
            platformContext = platformContext,
            settingsManager = settingsManager,
            themeRefreshNonce = themeRefreshNonce,
        ) {
            LaunchedEffect(Unit) {
                DesktopToastBus.messages.collect { msg ->
                    DesktopToastBus.snackbarHostState.showSnackbar(msg)
                }
            }
            Scaffold(
                snackbarHost = { SnackbarHost(DesktopToastBus.snackbarHostState) },
                containerColor = MaterialTheme.colorScheme.background
            ) { padding ->
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
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
                                event.isAltPressed && !mod && event.key == Key.Tab -> {
                                    DesktopNavigationHooks.cycleAdminTab?.invoke(!event.isShiftPressed)
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
    }
}
