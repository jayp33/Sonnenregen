package de.jayp33.sonnenregen.domain

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Clock time in the forecast's own offset, not the device's timezone. */
internal val CLOCK_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

internal fun parseTimestamp(value: String): OffsetDateTime = try {
    OffsetDateTime.parse(value)
} catch (_: Exception) {
    LocalDateTime.parse(value).atZone(ZoneId.of(TIMEZONE)).toOffsetDateTime()
}

internal fun parseTimestampOrNull(value: String?): OffsetDateTime? = try {
    value?.let(::parseTimestamp)
} catch (_: Exception) {
    null
}
