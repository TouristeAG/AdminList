package com.eventmanager.app.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
actual fun getScreenWidthDp(): Dp = LocalConfiguration.current.screenWidthDp.dp

@Composable
actual fun getScreenHeightDp(): Dp = LocalConfiguration.current.screenHeightDp.dp
