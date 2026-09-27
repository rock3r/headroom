package dev.sebastiano.headroom.quota

import dev.sebastiano.headroom.model.QuotaWindow
import dev.sebastiano.headroom.model.WindowKind
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

// Window lengths the providers use. A "month" is 30 days, as in the providers' own maths.
internal val FIVE_HOURS: Duration = Duration.ofHours(5)
internal val ONE_DAY: Duration = Duration.ofDays(1)
internal val ONE_WEEK: Duration = Duration.ofDays(7)
internal val THIRTY_DAYS: Duration = Duration.ofDays(30)

internal const val MIN_PERCENT = 0.0
internal const val MAX_PERCENT = 100.0

/** Builds a [QuotaWindow] whose [WindowKind] follows from [length] unless [kind] is given. */
internal fun quotaWindow(
    id: String,
    label: String,
    usedPercent: Double,
    resetsAt: Instant?,
    length: Duration?,
    group: String? = null,
    kind: WindowKind = WindowKind.fromLength(length),
    isUnlimited: Boolean = false,
): QuotaWindow =
    QuotaWindow(
        id = id,
        label = label,
        kind = kind,
        usedPercent = usedPercent,
        resetsAt = resetsAt,
        length = length,
        group = group,
        isUnlimited = isUnlimited,
    )

/** `seven_day_opus` becomes `Seven day opus`. Used when a provider sends an unknown window. */
internal fun fallbackWindowLabel(id: String): String =
    id.replace('_', ' ').replaceFirstChar { it.titlecase(Locale.ROOT) }

internal fun JsonObject.objectOrNull(key: String): JsonObject? = this[key] as? JsonObject

internal fun JsonObject.arrayOrNull(key: String): JsonArray? = this[key] as? JsonArray

/** The primitive content of [key], or `null` when it is missing, JSON `null` or not a primitive. */
internal fun JsonObject.stringOrNull(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull

internal fun JsonObject.nonBlankStringOrNull(key: String): String? =
    stringOrNull(key)?.takeIf { it.isNotBlank() }

/** A number, also when the provider sends it as a string. */
internal fun JsonObject.doubleOrNull(key: String): Double? =
    (this[key] as? JsonPrimitive)?.doubleOrNull?.takeIf { it.isFinite() }

internal fun JsonObject.longOrNull(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull

internal fun JsonObject.booleanOrNull(key: String): Boolean? =
    (this[key] as? JsonPrimitive)?.booleanOrNull
