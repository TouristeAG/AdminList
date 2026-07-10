package com.eventmanager.app.data.sync

import android.content.Context
import com.eventmanager.app.platform.createPlatformContext

/** Android convenience: build [SettingsManager] from an Android [Context]. */
fun settingsManagerFor(context: Context): SettingsManager =
    SettingsManager(createPlatformContext(context))
