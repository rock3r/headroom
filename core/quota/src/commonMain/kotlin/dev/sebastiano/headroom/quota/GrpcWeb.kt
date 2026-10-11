package dev.sebastiano.headroom.quota

/**
 * Unary gRPC-web framing over HTTP/1.1, as grok.com's billing service speaks it. Each frame is one
 * flag byte (0 for a message, 0x80 for trailers), a four-byte big-endian length, and the payload.
 */
internal object GrpcWeb {
    private const val HEADER_LENGTH = 5
    private const val TRAILER_FLAG = 0x80
    private const val FLAG_MASK = 0xFF

    fun dataFrame(message: ByteArray): ByteArray = frame(0, message)

    fun trailerFrame(trailers: String): ByteArray =
        frame(TRAILER_FLAG, trailers.encodeToByteArray())

    /**
     * Reads the message and the gRPC status of a unary answer. A trailers-only answer has an empty
     * body and carries `grpc-status` in the HTTP [headers] (lower-case names) instead.
     *
     * @throws IllegalArgumentException when a frame is cut short, or when there is neither a frame
     *   nor a status header.
     */
    fun decode(body: ByteArray, headers: Map<String, String> = emptyMap()): GrpcWebAnswer {
        val headerStatus = headers["grpc-status"]?.trim()?.toIntOrNull()
        require(body.isNotEmpty() || headerStatus != null) {
            "gRPC-web answer has neither a frame nor a status"
        }
        var offset = 0
        var message = ByteArray(0)
        var trailers = emptyMap<String, String>()
        while (offset < body.size) {
            require(offset + HEADER_LENGTH <= body.size) { "gRPC-web frame header cut short" }
            val flags = body[offset].toInt() and FLAG_MASK
            val length = body.readIntBigEndian(offset + 1)
            offset += HEADER_LENGTH
            require(length >= 0 && offset + length <= body.size) { "gRPC-web frame cut short" }
            val payload = body.copyOfRange(offset, offset + length)
            offset += length
            if (flags and TRAILER_FLAG != 0) trailers = parseTrailers(payload)
            else message = payload
        }
        return GrpcWebAnswer(
            message = message,
            status = trailers["grpc-status"]?.toIntOrNull() ?: headerStatus ?: GRPC_OK,
            statusMessage = trailers["grpc-message"] ?: headers["grpc-message"],
        )
    }

    private fun frame(flags: Int, payload: ByteArray): ByteArray {
        val frame = ByteArray(HEADER_LENGTH + payload.size)
        frame[0] = flags.toByte()
        frame.writeIntBigEndian(1, payload.size)
        payload.copyInto(frame, HEADER_LENGTH)
        return frame
    }

    private fun parseTrailers(payload: ByteArray): Map<String, String> =
        payload
            .decodeAscii()
            .split("\r\n", "\n")
            .mapNotNull { line ->
                val separator = line.indexOf(':')
                if (separator <= 0) null
                else line.take(separator).trim().lowercase() to line.drop(separator + 1).trim()
            }
            .toMap()

    const val GRPC_OK: Int = 0
    const val GRPC_PERMISSION_DENIED: Int = 7
    const val GRPC_RESOURCE_EXHAUSTED: Int = 8
    const val GRPC_UNAUTHENTICATED: Int = 16
}

/** One decoded gRPC-web answer: the message bytes and the gRPC status. */
internal class GrpcWebAnswer(val message: ByteArray, val status: Int, val statusMessage: String?)

private const val BYTE_BITS = 8
private const val BYTE_MASK = 0xFF
private const val ASCII_MAX = 0x7F
private const val REPLACEMENT_CHARACTER = '\uFFFD'

/** The four bytes at [offset] as a big-endian (network order) int. */
private fun ByteArray.readIntBigEndian(offset: Int): Int =
    (0 until Int.SIZE_BYTES).fold(0) { value, index ->
        (value shl BYTE_BITS) or (this[offset + index].toInt() and BYTE_MASK)
    }

/** Writes [value] as four big-endian (network order) bytes at [offset]. */
private fun ByteArray.writeIntBigEndian(offset: Int, value: Int) {
    for (index in 0 until Int.SIZE_BYTES) {
        this[offset + index] = (value ushr ((Int.SIZE_BYTES - 1 - index) * BYTE_BITS)).toByte()
    }
}

/** Decodes US-ASCII, as gRPC trailers are: any other byte becomes U+FFFD. */
private fun ByteArray.decodeAscii(): String =
    CharArray(size) { index ->
            val byte = this[index].toInt() and BYTE_MASK
            if (byte <= ASCII_MAX) byte.toChar() else REPLACEMENT_CHARACTER
        }
        .concatToString()
