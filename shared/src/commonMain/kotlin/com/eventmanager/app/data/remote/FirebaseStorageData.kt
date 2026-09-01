package com.eventmanager.app.data.remote

import com.eventmanager.app.platform.PlatformContext

internal expect suspend fun firebaseStoragePutJpeg(
    bucket: String,
    path: String,
    jpegBytes: ByteArray,
    platformContext: PlatformContext?,
): String?

internal expect suspend fun firebaseStorageDeleteObject(
    bucket: String,
    path: String,
    platformContext: PlatformContext?,
): Boolean

internal expect suspend fun firebaseStorageGetBytes(
    bucket: String,
    path: String,
    platformContext: PlatformContext?,
): ByteArray?
