package com.eventmanager.app.ui.platform

import androidx.compose.runtime.Composable
import com.eventmanager.app.platform.PlatformContext

/** Passive NFC / PC-SC UID listener while a screen or dialog is active. */
@Composable
expect fun NfcUidListenerEffect(
    platformContext: PlatformContext,
    enabled: Boolean,
    onUidRead: (String) -> Unit,
    onScanStatus: (String?) -> Unit = {},
)
