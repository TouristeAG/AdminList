package com.eventmanager.app.ui.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay

/**
 * Compose Desktop pays a one-time cost the first time a [Popup] is created (org switcher, dialogs…).
 * A tiny invisible popup at startup moves that hitch off the user's first org-switcher click.
 */
@Composable
fun DesktopPopupWarmup() {
    var active by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(500)
        active = false
    }
    if (!active) return

    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset.Zero,
        onDismissRequest = {},
        properties = PopupProperties(focusable = false),
    ) {
        Box(Modifier.size(1.dp).alpha(0f))
    }
}
