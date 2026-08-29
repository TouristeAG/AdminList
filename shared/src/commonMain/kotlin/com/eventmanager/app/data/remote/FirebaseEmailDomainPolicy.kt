package com.eventmanager.app.data.remote

/**
 * Institution policy: only Google accounts whose email domain is in [allowedDomains] may access data.
 * Empty list = no restriction (any signed-in member email).
 */
object FirebaseEmailDomainPolicy {
    /** Normalize `@Foo.CH` / `foo.ch` → `foo.ch`. */
    fun normalizeDomain(raw: String): String =
        raw.trim().lowercase().removePrefix("@").trim().trimEnd('.')

    fun parseStoredList(raw: String): List<String> =
        raw.split(',', ';', '\n', '\r')
            .map { normalizeDomain(it) }
            .filter { it.isNotBlank() && it.contains('.') }
            .distinct()

    fun serialize(domains: List<String>): String =
        domains.map { normalizeDomain(it) }
            .filter { it.isNotBlank() && it.contains('.') }
            .distinct()
            .joinToString(",")

    fun domainOfEmail(email: String?): String? {
        val e = email?.trim()?.lowercase().orEmpty()
        val at = e.lastIndexOf('@')
        if (at <= 0 || at >= e.length - 1) return null
        return normalizeDomain(e.substring(at + 1))
    }

    /**
     * @return true if [email] may access when [allowedDomains] is the policy.
     * Empty [allowedDomains] means unrestricted.
     */
    fun isEmailAllowed(email: String?, allowedDomains: List<String>): Boolean {
        if (allowedDomains.isEmpty()) return true
        val domain = domainOfEmail(email) ?: return false
        return allowedDomains.any { it == domain }
    }

    fun denialMessage(email: String?, allowedDomains: List<String>): String {
        val shown = allowedDomains.joinToString(", ") { "@$it" }
        val mail = email?.takeIf { it.isNotBlank() } ?: "(no email)"
        return "Google account $mail is not allowed. Use an address ending with: $shown"
    }

    /** Map form for Firestore `metadata/config.allowedEmailDomains` (rules lookup by domain key). */
    fun domainsToFirestoreMap(domains: List<String>): Map<String, Boolean> =
        domains.map { normalizeDomain(it) }
            .filter { it.isNotBlank() }
            .associateWith { true }
}
