package com.eventmanager.app.data.remote

/**
 * Legacy Android signing fingerprints — kept for reference / Play Console setup only.
 * Firebase Sign-In uses localhost Web OAuth (same redirect URIs as Desktop); no SHA-1 per institution.
 */
object NoctuListAndroidSigning {
    const val PACKAGE_NAME = "com.eventmanager.app"

    /** Production / sideload release builds (Play App Signing may use a different SHA-1). */
    const val SHA1_RELEASE = "67:1C:E8:D6:DD:CC:01:5D:2C:62:C8:82:DC:5C:84:FA:05:EC:3D:29"

    /** Local dev builds signed with the standard Android debug keystore. */
    const val SHA1_DEBUG = "67:1C:E8:D6:DD:CC:01:5D:2C:62:C8:82:DC:5C:84:FA:05:EC:3D:29"
}
