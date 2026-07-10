package com.eventmanager.app.platform

import com.eventmanager.app.data.database.EventManagerDatabase

expect fun createDatabase(context: PlatformContext): EventManagerDatabase

expect fun openUrl(url: String)

expect fun vibrateShort(context: PlatformContext)

expect fun getSystemLocaleTag(): String
