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
}
