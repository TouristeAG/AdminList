package com.eventmanager.app.data.remote

import com.eventmanager.app.data.models.AccountTransfer
import com.eventmanager.app.data.models.AccountTransferSyncState
import com.eventmanager.app.data.models.AccountHolderType
import com.eventmanager.app.data.models.AccountTransferType
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class FirestoreCodecAndRulesShapeTest {
    @Test
    fun ruleCompatibleMap_exposesFlatBalanceAndJsonEnvelope() {
        val mapped = ruleCompatibleFirestoreMap(mapOf("balance" to 12.5, "updatedAt" to 1L))
        assertEquals(12.5, mapped["balance"])
        assertTrue((mapped["json"] as? String)?.contains("balance") == true)
    }

    @Test
    fun encodeDecodeRoundTrip_preservesStringsWithCommas() {
        val original = mapOf(
            "description" to "Beer, wine, and snacks",
            "amount" to -4.5,
            "ok" to true,
        )
        val encoded = FirestoreJsonCodec.toEnvelope(original).json
        val decoded = FirestoreJsonCodec.fromEnvelope(FirestoreJsonEnvelope(encoded))
        assertEquals("Beer, wine, and snacks", decoded["description"])
        assertEquals(-4.5, decoded["amount"])
        assertEquals(true, decoded["ok"])
    }

    @Test
    fun transferToMap_usesSourceReferenceAsIdempotentKey() {
        val transfer = AccountTransfer(
            transferId = "t1",
            sourceReference = "POS:abc",
            holderType = AccountHolderType.GUEST,
            holderId = "g1",
            holderName = "Ada",
            amount = -10.0,
            type = AccountTransferType.POS_SALE,
            syncState = AccountTransferSyncState.PENDING,
        )
        val gateway = GitLiveFirestoreGateway()
        val map = gateway.transferToMap(transfer)
        assertEquals("POS:abc", map["sourceReference"])
        assertEquals(AccountTransferSyncState.PENDING.name, map["syncState"])
    }

    @Test
    fun ruleCompatibleMap_preservesNestedAllowedEmailDomains() {
        val mapped = ruleCompatibleFirestoreMap(
            mapOf(
                "allowedEmailDomains" to mapOf("gmail.com" to true, "example.org" to true),
            ),
        )
        val domains = mapped["allowedEmailDomains"] as? Map<*, *>
        assertEquals(true, domains?.get("gmail.com"))
        assertEquals(true, domains?.get("example.org"))
    }

    @Test
    fun toFirestoreFieldMap_usesPlainMapsNotJsonObject() {
        val fields = toFirestoreFieldMap(
            mapOf(
                "role" to "admin",
                "balance" to 12.5,
                "allowedEmailDomains" to mapOf("gmail.com" to true),
            ),
        )
        assertEquals("admin", fields["role"])
        assertEquals(12.5, fields["balance"])
        val nested = fields["allowedEmailDomains"] as? Map<*, *>
        assertEquals(true, nested?.get("gmail.com"))
    }

    @Test
    fun fromJsonObject_mergesFlatFieldsWithEnvelope() {
        val envelope = FirestoreJsonCodec.toEnvelope(
            mapOf("name" to "Ada", "lastModified" to 10L),
        ).json
        val obj = JsonObject(
            mapOf(
                "json" to JsonPrimitive(envelope),
                "amount" to JsonPrimitive(10.0),
                "lastModified" to JsonPrimitive(99L),
            ),
        )
        val decoded = FirestoreJsonCodec.fromJsonObject(obj)
        assertEquals("Ada", decoded["name"])
        assertEquals(10.0, decoded["amount"])
        // Envelope lastModified overwrites the flat duplicate when both exist.
        assertEquals(10L, decoded["lastModified"])
    }

    @Test
    fun firestoreRulesShape_enforcesMemberReadRoleWhitelistAndTransferCatchAllExclusion() = runBlocking {
        // Prefer compose resource (in-app clipboard) so deploy file and app stay in sync.
        val fromResource = runCatching { FirestoreRulesClipboardContent.load() }.getOrNull()
        val fromRepo = sequenceOf(
            File("firebase/firestore.rules"),
            File("../firebase/firestore.rules"),
        ).firstOrNull { it.exists() }?.readText()
        val rules = fromResource ?: fromRepo
            ?: error("firestore.rules not found via resource or repo path")

        assertTrue(rules.contains("function emailDomainAllowed(orgId)"))
        assertTrue(rules.contains("allowedEmailDomains"))
        assertTrue(rules.contains("function isValidMemberRole(role)"))
        assertTrue(rules.contains("role in ['admin', 'member', 'door', 'pos']"))

        val membersBlock = Regex(
            """match /orgs/\{orgId\}/members/\{uid\} \{([\s\S]*?)\n    \}""",
        ).find(rules)?.groupValues?.get(1)
            ?: error("members match block missing")
        assertTrue(
            membersBlock.contains("allow read: if isMember(orgId)"),
            "members must require org membership to read",
        )
        assertFalse(
            membersBlock.contains("allow read: if isSignedIn()"),
            "members must not be readable by any signed-in user",
        )
        assertTrue(membersBlock.contains("request.resource.data.role == 'admin'"))
        assertTrue(
            membersBlock.contains("!exists(memberPath(orgId))"),
            "members bootstrap must allow self-admin when member doc is missing",
        )
        assertTrue(membersBlock.contains("isOrgAdmin(orgId)"))

        assertTrue(
            rules.contains("document[0] != 'transfers'"),
            "catch-all must exclude transfers writes",
        )
        assertTrue(rules.contains("allow update, delete: if false; // append-only ledger"))
    }
}