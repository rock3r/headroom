package dev.sebastiano.headroom.quota

import java.io.ByteArrayOutputStream
import java.time.Instant

/** One SuperGrok usage-limit reset token: its id and when it stops being valid. */
internal data class GrokResetToken(val id: String, val validUntil: Instant?)

/**
 * The protobuf messages of grok.com's `prod_mc_billing.ConsumerUiSvc` that the resets use. The
 * field numbers are the service's own:
 * ```
 * message ConsumerResetToken {
 *   string token_id = 10;
 *   google.protobuf.Timestamp validity_start = 20;
 *   google.protobuf.Timestamp validity_end = 30;
 * }
 * message ConsumerGetRemainingResetsReq {}
 * message ConsumerGetRemainingResetsResp { repeated ConsumerResetToken tokens = 10; }
 * message ConsumerRedeemResetReq { string token_id = 10; }
 * message ConsumerRedeemResetResp { repeated ConsumerResetToken still_redeemable = 10; }
 * ```
 *
 * Both answers keep their tokens in field 10, so one reader serves both.
 */
internal object GrokResetProto {
    private const val TOKENS_FIELD = 10
    private const val TOKEN_ID_FIELD = 10
    private const val VALIDITY_END_FIELD = 30
    private const val SECONDS_FIELD = 1
    private const val NANOS_FIELD = 2

    fun listRequest(): ByteArray = ByteArray(0)

    fun redeemRequest(tokenId: String): ByteArray =
        ProtoWriter().apply { string(TOKEN_ID_FIELD, tokenId) }.toByteArray()

    /**
     * The tokens of a list or redeem answer.
     *
     * @throws IllegalArgumentException when the message is cut short or malformed.
     */
    fun readTokens(bytes: ByteArray): List<GrokResetToken> {
        val tokens = mutableListOf<GrokResetToken>()
        ProtoReader(bytes).forEachField { field, reader ->
            if (field == TOKENS_FIELD && reader.wireType == ProtoReader.LENGTH_DELIMITED) {
                tokens += readToken(reader.bytes())
            } else {
                reader.skip()
            }
        }
        return tokens
    }

    /** Encodes an answer with [tokens]: the inverse of [readTokens], for tests and fakes. */
    fun tokensMessage(tokens: List<GrokResetToken>): ByteArray =
        ProtoWriter()
            .apply {
                tokens.forEach { token ->
                    val message =
                        ProtoWriter().apply {
                            string(TOKEN_ID_FIELD, token.id)
                            token.validUntil?.let { until ->
                                message(
                                    VALIDITY_END_FIELD,
                                    ProtoWriter().apply {
                                        varint(SECONDS_FIELD, until.epochSecond)
                                        if (until.nano != 0) {
                                            varint(NANOS_FIELD, until.nano.toLong())
                                        }
                                    },
                                )
                            }
                        }
                    message(TOKENS_FIELD, message)
                }
            }
            .toByteArray()

    private fun readToken(bytes: ByteArray): GrokResetToken {
        var id = ""
        var validUntil: Instant? = null
        ProtoReader(bytes).forEachField { field, reader ->
            when {
                field == TOKEN_ID_FIELD && reader.wireType == ProtoReader.LENGTH_DELIMITED ->
                    id = reader.bytes().toString(Charsets.UTF_8)
                field == VALIDITY_END_FIELD && reader.wireType == ProtoReader.LENGTH_DELIMITED ->
                    validUntil = readTimestamp(reader.bytes())
                else -> reader.skip()
            }
        }
        return GrokResetToken(id, validUntil)
    }

    private fun readTimestamp(bytes: ByteArray): Instant {
        var seconds = 0L
        var nanos = 0L
        ProtoReader(bytes).forEachField { field, reader ->
            when {
                field == SECONDS_FIELD && reader.wireType == ProtoReader.VARINT ->
                    seconds = reader.varint()
                field == NANOS_FIELD && reader.wireType == ProtoReader.VARINT ->
                    nanos = reader.varint()
                else -> reader.skip()
            }
        }
        return Instant.ofEpochSecond(seconds, nanos)
    }
}

/** Writes protobuf fields: just the wire types the reset messages use. */
internal class ProtoWriter {
    private val out = ByteArrayOutputStream()

    fun varint(field: Int, value: Long) {
        writeVarint((field.toLong() shl TAG_SHIFT) or ProtoReader.VARINT.toLong())
        writeVarint(value)
    }

    fun string(field: Int, value: String) = bytes(field, value.toByteArray(Charsets.UTF_8))

    fun message(field: Int, message: ProtoWriter) = bytes(field, message.toByteArray())

    fun toByteArray(): ByteArray = out.toByteArray()

    private fun bytes(field: Int, value: ByteArray) {
        writeVarint((field.toLong() shl TAG_SHIFT) or ProtoReader.LENGTH_DELIMITED.toLong())
        writeVarint(value.size.toLong())
        out.write(value)
    }

    private fun writeVarint(value: Long) {
        var rest = value
        while (rest and SEVEN_BITS.inv() != 0L) {
            out.write(((rest and SEVEN_BITS) or CONTINUATION).toInt())
            rest = rest ushr BITS_PER_BYTE
        }
        out.write(rest.toInt())
    }

    private companion object {
        const val TAG_SHIFT = 3
        const val SEVEN_BITS = 0x7FL
        const val CONTINUATION = 0x80L
        const val BITS_PER_BYTE = 7
    }
}

/** Reads protobuf fields one by one, skipping those the caller does not want. */
internal class ProtoReader(private val bytes: ByteArray) {
    private var offset = 0

    /** The wire type of the field being read. */
    var wireType: Int = 0
        private set

    /** Calls [block] for each field; [block] must read or [skip] the field's value. */
    fun forEachField(block: (field: Int, reader: ProtoReader) -> Unit) {
        while (offset < bytes.size) {
            val tag = varint()
            wireType = (tag and WIRE_TYPE_MASK).toInt()
            val field = (tag ushr TAG_SHIFT).toInt()
            require(field > 0) { "protobuf field number 0" }
            block(field, this)
        }
    }

    fun varint(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            require(offset < bytes.size) { "protobuf varint cut short" }
            require(shift < MAX_VARINT_SHIFT) { "protobuf varint too long" }
            val byte = bytes[offset++].toLong() and BYTE_MASK
            result = result or ((byte and SEVEN_BITS) shl shift)
            if (byte and CONTINUATION == 0L) return result
            shift += BITS_PER_BYTE
        }
    }

    fun bytes(): ByteArray {
        val length = varint()
        require(length >= 0 && offset + length <= bytes.size) { "protobuf field cut short" }
        val end = offset + length.toInt()
        return bytes.copyOfRange(offset, end).also { offset = end }
    }

    fun skip() {
        when (wireType) {
            VARINT -> varint()
            FIXED64 -> advance(FIXED64_BYTES)
            LENGTH_DELIMITED -> bytes()
            FIXED32 -> advance(FIXED32_BYTES)
            else -> throw IllegalArgumentException("protobuf wire type $wireType not supported")
        }
    }

    private fun advance(count: Int) {
        require(offset + count <= bytes.size) { "protobuf field cut short" }
        offset += count
    }

    companion object {
        const val VARINT: Int = 0
        const val FIXED64: Int = 1
        const val LENGTH_DELIMITED: Int = 2
        const val FIXED32: Int = 5
        private const val TAG_SHIFT = 3
        private const val WIRE_TYPE_MASK = 0x7L
        private const val SEVEN_BITS = 0x7FL
        private const val CONTINUATION = 0x80L
        private const val BYTE_MASK = 0xFFL
        private const val BITS_PER_BYTE = 7
        private const val MAX_VARINT_SHIFT = 64
        private const val FIXED64_BYTES = 8
        private const val FIXED32_BYTES = 4
    }
}
