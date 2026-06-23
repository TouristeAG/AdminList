package com.eventmanager.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.eventmanager.app.platform.PlatformContext

@Composable
actual fun BleReaderScannerStatusFooter(
    platformContext: PlatformContext,
    isExternalReaderBusy: Boolean,
    labelColor: Color,
    activeColor: Color,
    idleWarnColor: Color,
    modifier: Modifier
) { }

@Composable
actual fun BleReaderPickerDialog(
    platformContext: PlatformContext,
    onDismiss: () -> Unit,
    onPicked: (mac: String, name: String) -> Unit
) {
    onDismiss()
}
