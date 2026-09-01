package com.eventmanager.app.data.remote

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Encode/decode Firestore REST v1 typed [fields] maps.
 * https://firebase.google.com/docs/firestore/reference/rest/v1/projects.databases.documents
 */
internal object DesktopFirestoreRestCodec {
    fun decodeDocumentFields(fields: JsonObject?): Map<String, Any?> {
        if (fields == null) return emptyMap()
        val raw = fields.mapValues { (_, value) -> decodeValue(value) }
        return FirestoreJsonCodec.snapshotToMap(raw)
    }

    fun encodeDocumentFields(data: Map<String, Any?>): JsonObject {
        val stamped = ruleCompatibleFirestoreMap(data)
        return buildJsonObject {
            stamped.forEach { (key, value) ->
                put(key, encodeValue(value))
            }
        }
    }

    private fun decodeValue(element: JsonElement): Any? {
        val obj = element.jsonObject
        return when {
            obj.containsKey("nullValue") -> null
            obj.containsKey("booleanValue") -> obj["booleanValue"]!!.jsonPrimitive.content == "true"
            obj.containsKey("integerValue") -> obj["integerValue"]!!.jsonPrimitive.content.toLongOrNull()
                ?: obj["integerValue"]!!.jsonPrimitive.content
            obj.containsKey("doubleValue") -> obj["doubleValue"]!!.jsonPrimitive.content.toDoubleOrNull()
                ?: obj["doubleValue"]!!.jsonPrimitive.content
            obj.containsKey("stringValue") -> obj["stringValue"]!!.jsonPrimitive.contentOrNull
            obj.containsKey("mapValue") -> {
                val nested = obj["mapValue"]!!.jsonObject["fields"]?.jsonObject
                nested?.mapValues { (_, v) -> decodeValue(v) } ?: emptyMap<String, Any?>()
            }
            obj.containsKey("arrayValue") -> {
                val values = obj["arrayValue"]!!.jsonObject["values"] as? JsonArray
                values?.map { decodeValue(it) } ?: emptyList<Any?>()
            }
            else -> element.toString()
        }
    }

    private fun encodeValue(value: Any?): JsonElement = when (value) {
        null -> buildJsonObject { put("nullValue", JsonNull) }
        is Boolean -> buildJsonObject { put("booleanValue", JsonPrimitive(value)) }
        is Int -> buildJsonObject { put("integerValue", JsonPrimitive(value.toString())) }
        is Long -> buildJsonObject { put("integerValue", JsonPrimitive(value.toString())) }
        is Float -> buildJsonObject { put("doubleValue", JsonPrimitive(value.toDouble())) }
        is Double -> buildJsonObject { put("doubleValue", JsonPrimitive(value)) }
        is Number -> buildJsonObject { put("doubleValue", JsonPrimitive(value.toDouble())) }
        is String -> buildJsonObject { put("stringValue", JsonPrimitive(value)) }
        is Map<*, *> -> buildJsonObject {
            put(
                "mapValue",
                buildJsonObject {
                    put(
                        "fields",
                        buildJsonObject {
                            value.forEach { (k, v) ->
                                val key = k?.toString()?.takeIf { it.isNotBlank() } ?: return@forEach
                                put(key, encodeValue(v))
                            }
                        },
                    )
                },
            )
        }
        is Iterable<*> -> buildJsonObject {
            put(
                "arrayValue",
                buildJsonObject {
                    put(
                        "values",
                        JsonArray(value.map { encodeValue(it) }),
                    )
                },
            )
        }
        else -> buildJsonObject { put("stringValue", JsonPrimitive(value.toString())) }
    }
}
