package dev.sebastiano.headroom.auth

/**
 * The parts of a URI that the sign-in checks read: is a verification page https, is a pasted URL
 * the loopback callback. It follows the grammar of `java.net.URI` (RFC 2396 with IPv6 hosts), which
 * these checks used before, so a URI is accepted or refused the same way on every platform:
 * - text that is not a URI, such as one with a space or a bad `%` escape, does not parse;
 * - [host] is null when the authority is not a server (`host[:port]`), as for `under_score.com`;
 * - [port] is -1 when there is none, and [path] is decoded, and empty for an opaque URI such as
 *   `mailto:`.
 *
 * Non-ASCII characters are refused everywhere, where `java.net.URI` allowed some in paths and
 * queries. No sign-in URI has any.
 */
internal class WebUri
private constructor(val scheme: String?, val host: String?, val port: Int, val path: String) {

    internal companion object {
        /** [value] parsed, or null when it is not a URI. */
        fun parseOrNull(value: String): WebUri? =
            try {
                parse(value)
            } catch (_: IllegalArgumentException) {
                null
            }

        private fun parse(value: String): WebUri {
            val colon = value.indexOfFirst { it == ':' || it in "/?#" }
            if (colon < 0 || value[colon] != ':') return hierarchical(scheme = null, value)
            require(colon > 0) { "Expected a scheme name" }
            val scheme = value.substring(0, colon)
            require(scheme.first().isAsciiLetter() && scheme.all(::isSchemeChar)) { "Bad scheme" }
            val rest = value.substring(colon + 1)
            require(rest.isNotEmpty()) { "Expected a scheme-specific part" }
            if (rest.startsWith('/')) return hierarchical(scheme, rest)
            requireChars(rest.substringBefore('#'), URIC)
            requireChars(rest.substringAfter('#', ""), URIC)
            return WebUri(scheme, host = null, port = -1, path = "")
        }

        /** `[//authority][path][?query][#fragment]`. */
        private fun hierarchical(scheme: String?, part: String): WebUri {
            val beforeFragment = part.substringBefore('#')
            requireChars(part.substringAfter('#', ""), URIC)
            val beforeQuery = beforeFragment.substringBefore('?')
            requireChars(beforeFragment.substringAfter('?', ""), URIC)
            if (!beforeQuery.startsWith("//")) {
                requireChars(beforeQuery, PATH)
                return WebUri(
                    scheme,
                    host = null,
                    port = -1,
                    path = percentDecode(beforeQuery, plusIsSpace = false),
                )
            }
            val authorityAndPath = beforeQuery.substring(2)
            val slash = authorityAndPath.indexOf('/')
            val authority = if (slash < 0) authorityAndPath else authorityAndPath.take(slash)
            val path = if (slash < 0) "" else authorityAndPath.substring(slash)
            requireChars(path, PATH)
            val server = serverOrNull(authority)
            return WebUri(
                scheme,
                server?.host,
                server?.port ?: -1,
                percentDecode(path, plusIsSpace = false),
            )
        }

        /**
         * The host and port of a server authority. When [authority] is not one, null if it is a
         * valid registry name; otherwise the URI is invalid.
         */
        private fun serverOrNull(authority: String): Server? {
            if (authority.isEmpty()) return null
            val server = runCatching { server(authority) }
            server.getOrNull()?.let {
                return it
            }
            require(isAll(authority, REG_NAME)) { "Bad authority" }
            return null
        }

        /** `[userinfo@]host[:port]`. */
        private fun server(authority: String): Server {
            val at = authority.indexOf('@')
            if (at >= 0) requireChars(authority.take(at), USERINFO)
            val hostAndPort = authority.substring(at + 1)
            val host: String
            val afterHost: String
            if (hostAndPort.startsWith('[')) {
                val close = hostAndPort.indexOf(']')
                require(close > 0) { "Expected ]" }
                host = hostAndPort.take(close + 1)
                require(isIpv6(host.substring(1, host.length - 1))) { "Bad IPv6 address" }
                afterHost = hostAndPort.substring(close + 1)
            } else {
                val colon = hostAndPort.indexOf(':')
                host = if (colon < 0) hostAndPort else hostAndPort.take(colon)
                require(isIpv4(host) || isHostname(host)) { "Bad host" }
                afterHost = if (colon < 0) "" else hostAndPort.substring(colon)
            }
            if (afterHost.isEmpty()) return Server(host, port = -1)
            require(afterHost.startsWith(':')) { "Expected the end of the authority" }
            val digits = afterHost.substring(1)
            require(digits.all { it in '0'..'9' }) { "Bad port" }
            return Server(host, if (digits.isEmpty()) -1 else digits.toInt())
        }

        private class Server(val host: String, val port: Int)

        private fun isIpv4(host: String): Boolean {
            val parts = host.split('.')
            return parts.size == IPV4_PARTS &&
                parts.all { part ->
                    part.isNotEmpty() &&
                        part.length <= IPV4_DIGITS &&
                        part.all { it in '0'..'9' } &&
                        part.toInt() <= IPV4_MAX
                }
        }

        private fun isIpv6(address: String): Boolean =
            ':' in address && address.all { it.isAsciiHexDigit() || it == ':' || it == '.' }

        /**
         * Dot-separated labels of letters, digits and inner dashes; the last starts with a letter.
         */
        private fun isHostname(host: String): Boolean {
            val labels = host.removeSuffix(".").split('.')
            return labels.all { label ->
                label.isNotEmpty() &&
                    label.first().isAsciiLetterOrDigit() &&
                    label.last().isAsciiLetterOrDigit() &&
                    label.all { it.isAsciiLetterOrDigit() || it == '-' }
            } && labels.last().first().isAsciiLetter()
        }

        /** Checks that [part] only has [allowed] characters and well-formed `%XX` escapes. */
        private fun requireChars(part: String, allowed: String) {
            require(isAll(part, allowed)) { "Illegal character" }
        }

        private fun isAll(part: String, allowed: String): Boolean {
            var index = 0
            while (index < part.length) {
                val char = part[index]
                if (char == '%') {
                    if (index + 2 >= part.length) return false
                    if (!part[index + 1].isAsciiHexDigit() || !part[index + 2].isAsciiHexDigit()) {
                        return false
                    }
                    index += ESCAPE_LENGTH
                    continue
                }
                if (!char.isAsciiLetterOrDigit() && char !in allowed) return false
                index++
            }
            return true
        }

        private fun isSchemeChar(char: Char): Boolean = char.isAsciiLetterOrDigit() || char in "+-."

        private fun Char.isAsciiLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'

        private fun Char.isAsciiLetterOrDigit(): Boolean = isAsciiLetter() || this in '0'..'9'

        private fun Char.isAsciiHexDigit(): Boolean =
            this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

        private const val IPV4_PARTS = 4
        private const val IPV4_DIGITS = 3
        private const val IPV4_MAX = 255
        private const val ESCAPE_LENGTH = 3

        // RFC 2396 character classes, without letters and digits, which every class allows.
        private const val MARK = "-_.!~*'()"
        private const val RESERVED = ";/?:@&=+$,[]"
        private const val URIC = RESERVED + MARK
        private const val PATH = "$MARK:@&=+$,;/"
        private const val USERINFO = "$MARK;:&=+$,"
        private const val REG_NAME = "$MARK$,;:@&=+"
    }
}
