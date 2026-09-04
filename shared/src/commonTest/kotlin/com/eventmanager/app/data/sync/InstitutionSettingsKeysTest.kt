package com.eventmanager.app.data.sync

import kotlin.test.Test
import kotlin.test.assertTrue

class InstitutionSettingsKeysTest {
    @Test
    fun profilePhotosEnabledIsSyncedAcrossOrgDevices() {
        assertTrue(InstitutionSettingsKeys.PROFILE_PHOTOS_ENABLED in InstitutionSettingsKeys.ALL)
    }

    @Test
    fun announcementsBilleterieSendIsSyncedAcrossOrgDevices() {
        assertTrue(InstitutionSettingsKeys.ANNOUNCEMENTS_NON_ADMIN_SEND_ENABLED in InstitutionSettingsKeys.ALL)
    }
}
