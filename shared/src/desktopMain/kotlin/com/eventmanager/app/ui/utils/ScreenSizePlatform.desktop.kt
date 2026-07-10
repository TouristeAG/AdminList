package com.eventmanager.app.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp

@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun getScreenWidthDp(): Dp {
    val density = LocalDensity.current
    return with(density) { LocalWindowInfo.current.containerSize.width.toDp() }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun getScreenHeightDp(): Dp {
    val density = LocalDensity.current
    return with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
}
