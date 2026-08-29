package com.eventmanager.app.data.remote

/**
 * Flat Firestore payloads so [firebase/firestore.rules] can read `role`, `balance`, `value`, etc.
 * Also embeds a legacy `json` envelope for older readers.
 */
internal fun sanitizeFirestoreValue(value: Any?): Any? = when (value) {
    null -> null
    is Boolean -> value
    is Int -> value
    is Long -> value
    is Float -> value.toDouble()
    is Double -> value
    is Number -> value.toDouble()
    else -> value.toString()
}

internal fun sanitizeFirestoreMap(data: Map<String, Any?>): Map<String, Any?> =
    data.mapValues { (_, v) -> sanitizeFirestoreValue(v) }

internal fun ruleCompatibleFirestoreMap(data: Map<String, Any?>): Map<String, Any?> {
    val flat = sanitizeFirestoreMap(data).toMutableMap()
    flat["json"] = FirestoreJsonCodec.toEnvelope(data).json
  // Nested maps (e.g. allowedEmailDomains) must stay maps for rules — restore from source.
    data.forEach { (key, value) ->
        if (value is Map<*, *>) {
            flat[key] = toFirestoreFieldValue(value)
        }
    }
    return flat
}

/**
 * GitLive Firestore [set] uses [dev.gitlive.firebase.internal.FirebaseEncoder], not [JsonEncoder].
 * Never pass [kotlinx.serialization.json.JsonObject] — it crashes with
 * "Expected Encoder to be JsonEncoder, got FirebaseEncoder".
 */
internal fun toFirestoreFieldMap(data: Map<String, Any?>): Map<String, Any?> =
    data.mapValues { (_, value) -> toFirestoreFieldValue(value) }

internal fun toFirestoreFieldValue(value: Any?): Any? = when (value) {
    null -> null
    is Boolean -> value
    is Int -> value
    is Long -> value
    is Float -> value.toDouble()
    is Double -> value
    is Number -> value.toDouble()
    is String -> value
    is Map<*, *> -> value.entries
        .mapNotNull { (k, v) -> k?.toString()?.takeIf { it.isNotBlank() }?.let { it to toFirestoreFieldValue(v) } }
        .toMap()
    is Iterable<*> -> value.map { toFirestoreFieldValue(it) }
    else -> value.toString()
}
