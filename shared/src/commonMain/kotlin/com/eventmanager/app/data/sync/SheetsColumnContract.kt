package com.eventmanager.app.data.sync

/**
 * Fixed Google Sheets column headers. Profile photo fields must never appear here —
 * photos are Firebase Storage + Firestore only.
 */
internal object SheetsColumnContract {
    val GUEST_LIST = listOf(
        "Name", "Email", "Phone", "Invitations", "Venue", "Notes",
        "Volunteer Benefit", "Last Modified", "NFC UID", "ID", "Admin",
    )
    val VOLUNTEER_GUEST_LIST = listOf(
        "Name", "Last Name Abbreviation", "Invitations", "Venue", "Notes",
        "Volunteer Benefit", "Last Modified", "NFC UID",
    )
    val VOLUNTEERS = listOf(
        "ID", "Name", "Abbreviation", "Email", "Phone", "Date of Birth",
        "Gender", "Rank", "Active", "Last Modified", "NFC UID", "Admin",
    )
}
