package com.eventmanager.app.ui.platform

import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object DesktopToastBus {
    val snackbarHostState = SnackbarHostState()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    fun show(message: String) {
        _messages.tryEmit(message)
    }
}
