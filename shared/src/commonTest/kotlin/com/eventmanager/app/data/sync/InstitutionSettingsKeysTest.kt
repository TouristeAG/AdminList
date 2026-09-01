package com.eventmanager.app.data.sync

import kotlin.test.Test
import kotlin.test.assertTrue

class InstitutionSettingsKeysTest {
    @Test
    fun profilePhotosEnabledIsSyncedAcrossOrgDevices() {
        assertTrue(InstitutionSettingsKeys.PROFILE_PHOTOS_ENABLED in InstitutionSettingsKeys.ALL)
    }
}
