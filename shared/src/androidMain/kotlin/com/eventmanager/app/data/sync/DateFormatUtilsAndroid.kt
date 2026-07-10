package com.eventmanager.app.data.sync

import android.content.Context
import com.eventmanager.app.platform.createPlatformContext

fun formatRelativeSinceSync(context: Context, syncTimeMillis: Long, nowMillis: Long = System.currentTimeMillis()): String =
    DateFormatUtils.formatRelativeSinceSync(createPlatformContext(context), syncTimeMillis, nowMillis)

fun formatSyncPillTimeAgo(context: Context, syncTimeMillis: Long, nowMillis: Long = System.currentTimeMillis()): String =
    DateFormatUtils.formatSyncPillTimeAgo(createPlatformContext(context), syncTimeMillis, nowMillis)

fun formatDateTime(timestamp: Long, context: Context): String =
    DateFormatUtils.formatDateTime(timestamp, createPlatformContext(context))

fun formatDate(timestamp: Long, context: Context): String =
    DateFormatUtils.formatDate(timestamp, createPlatformContext(context))
