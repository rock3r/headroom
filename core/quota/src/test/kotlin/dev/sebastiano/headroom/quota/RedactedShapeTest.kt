package dev.sebastiano.headroom.quota

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json

class RedactedShapeTest {
    @Test
    fun `text is hidden but numbers and structure stay`() {
        val json =
            Json.parseToJsonElement(
                """{"license":"lic-123","current":{"amount":"12.5"},""" +
                    """"until":1790000000000,"ok":true,"tags":["a"]}"""
            )

        assertEquals(
            """{license: <text, 7 chars>, current: {amount: "12.5"}, """ +
                """until: 1790000000000, ok: true, tags: [<text, 1 chars>]}""",
            redactedShape(json),
        )
    }

    @Test
    fun `without numbers, hides numbers too but keeps booleans and null`() {
        val json =
            Json.parseToJsonElement(
                """{"licenseId":"12345","orgId":678,"aiAccessEnabled":true,"orgName":null}"""
            )

        assertEquals(
            """{licenseId: <number, 5 chars>, orgId: <number, 3 chars>, """ +
                """aiAccessEnabled: true, orgName: null}""",
            redactedShape(json, keepNumbers = false),
        )
    }

    @Test
    fun `a safe word is a short single word, and anything else shows only its length`() {
        assertEquals("license", safeWord("license"))
        assertEquals("invalid_grant", safeWord("invalid_grant"))
        assertEquals("<text, 11 chars>", safeWord("Sam Example"))
        assertEquals("<text, 15 chars>", safeWord("sam@example.com"))
        assertEquals("<text, 0 chars>", safeWord(""))
    }

    @Test
    fun `error descriptions keep their words and lose ids, emails and tokens`() {
        assertEquals(
            "Unknown org_id <id> for <email>, token <id>",
            maskIds(
                "Unknown org_id 0b7f3c1e-8d2a-4c5b-9e6f-123456789abc for sam@example.com, " +
                    "token abcdefghijklmnopqrstuvwxyz0123"
            ),
        )
    }
}
