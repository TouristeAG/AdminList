package com.eventmanager.app.data.sync

import com.eventmanager.app.platform.PlatformContext
import com.eventmanager.app.platform.createAppStorage

fun settingsManagerFor(platformContext: PlatformContext): SettingsManager =
    SettingsManager(createAppStorage(platformContext))
