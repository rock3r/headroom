package dev.sebastiano.headroom.quota

import java.net.URLEncoder
import kotlin.test.Test
import kotlin.test.assertEquals

class FormEncodeTest {
    @Test
    fun `encodes the same way as URLEncoder`() {
        listOf(
                "plain",
                "a b+c&d=e",
                "~tilde*star.dot-dash_under",
                "ünïcødé ✓ 🎉",
                "https://example.com/a?b=c#d",
                "%41",
                "",
            )
            .forEach { value ->
                assertEquals(URLEncoder.encode(value, Charsets.UTF_8), formEncode(value), value)
            }
    }
}
