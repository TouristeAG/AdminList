package com.eventmanager.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.eventmanager.app.data.models.Guest
import com.eventmanager.app.ui.viewmodel.EventManagerViewModel

@Composable
fun DesktopBilleterieHomeScreen(
    guests: List<Guest>,
    viewModel: EventManagerViewModel,
    onBack: () -> Unit,
    onOpenGuestList: () -> Unit,
    onOpenScanner: () -> Unit,
    onOpenPos: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    WideBilleterieHomeScreen(
        guests = guests,
        viewModel = viewModel,
        onBack = onBack,
        onOpenGuestList = onOpenGuestList,
        onOpenScanner = onOpenScanner,
        onOpenPos = onOpenPos,
        onOpenSettings = onOpenSettings,
        modifier = modifier,
        hoverEnabled = true,
    )
}
