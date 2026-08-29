package com.eventmanager.app.data.security

import com.eventmanager.app.ui.components.ScannerMatch

/** Result of verifying local admin rights (`isAdmin` on guest/volunteer). */
sealed class LocalAdminAccessResult {
    data class Granted(val match: ScannerMatch, val displayName: String) : LocalAdminAccessResult()
    data class Denied(val displayName: String) : LocalAdminAccessResult()
    data object NotFound : LocalAdminAccessResult()
}

fun ScannerMatch.displayName(): String = when (this) {
    is ScannerMatch.VolunteerMatch -> volunteer.name
    is ScannerMatch.GuestMatch -> guest.name
}

fun ScannerMatch.isAdminFlag(): Boolean = when (this) {
    is ScannerMatch.VolunteerMatch -> volunteer.isAdmin
    is ScannerMatch.GuestMatch -> guest.isAdmin
}
