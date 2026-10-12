package dev.sebastiano.headroom.auth

import java.net.URLDecoder
import kotlin.test.Test
import kotlin.test.assertEquals

/** [decodeQueryComponent] must decode callback parameters the way `URLDecoder` did. */
class QueryDecodeJvmTest {
    @Test
    fun `decodes the same way as URLDecoder`() {
        listOf(
                "plain",
                "a+b",
                "a%20b",
                "%E2%9C%93%20done",
                "%C3%A9t%C3%A9",
                "%2B%2F%3D",
                "%41%4a%4A",
                "trailing%",
                "trailing%4",
                "bad%zzescape",
                "negative%-1",
                "plus%+1",
                "%FF%FE",
                "ünïcødé",
                "",
            )
            .forEach { value ->
                val expected = runCatching { URLDecoder.decode(value, Charsets.UTF_8) }
                val actual = runCatching { decodeQueryComponent(value) }
                assertEquals(expected.getOrNull(), actual.getOrNull(), value)
                assertEquals(expected.isFailure, actual.isFailure, value)
            }
    }
}
