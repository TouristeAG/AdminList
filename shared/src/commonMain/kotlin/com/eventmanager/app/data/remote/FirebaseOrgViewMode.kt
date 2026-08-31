package com.eventmanager.app.data.remote

/** How Billetterie/POS aggregate configured Firebase organizations locally. */
enum class FirebaseOrgViewMode {
    SINGLE,
    ALL,
    ;

    companion object {
        fun fromStorage(raw: String?): FirebaseOrgViewMode =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: SINGLE
    }
}

/** Sentinel value for the combined view — never stored as a Firestore org id. */
const val FIREBASE_ORG_ALL_SENTINEL = "__ALL__"

fun isFirebaseOrgAllSentinel(orgId: String): Boolean =
    orgId.trim() == FIREBASE_ORG_ALL_SENTINEL

/** Org id safe for Firestore member/config writes (never the All-orgs sentinel). */
fun resolveWritableFirebaseOrgId(
    activeOrgId: String,
    lastSingleOrgId: String,
    configuredOrgIds: List<String>,
): String {
    val active = activeOrgId.trim()
    if (active.isNotBlank() && !isFirebaseOrgAllSentinel(active)) return active
    val last = lastSingleOrgId.trim()
    if (last.isNotBlank() && !isFirebaseOrgAllSentinel(last)) return last
    return configuredOrgIds.map { it.trim() }.firstOrNull { it.isNotBlank() && !isFirebaseOrgAllSentinel(it) }.orEmpty()
}
