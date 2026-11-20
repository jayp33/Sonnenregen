package de.jayp33.sonnenregen.domain

import java.time.Duration
import java.time.OffsetDateTime

enum class SlotSource { CURRENT, NOWCAST, HOURLY, INTERPOLATED }

enum class Event { STARTS, ENDS }

data class Slot(
    val start: OffsetDateTime,
    val duration: Duration,
    val symbol: String,
    val source: SlotSource,
    val solarElevation: Double? = null,
    val night: Boolean? = null,
) {
    val end: OffsetDateTime get() = start.plus(duration)

    val sunny: Boolean
        get() {
            if (night == true || (solarElevation != null && solarElevation <= 0.0)) return false
            return isSunSymbol(symbol)
        }

    val raining: Boolean get() = isRainSymbol(symbol)
}

fun isSunSymbol(symbol: String): Boolean =
    symbol.startsWith("so") || symbol.startsWith("wb")

fun isRainSymbol(symbol: String): Boolean {
    val detail = symbol.drop(2)
    if ('n' in detail) return false // snow
    return detail.any { it == 'r' || it == 's' || it == 'g' }
}

data class Change(
    val currently: Boolean,
    val event: Event?,
    val at: OffsetDateTime?,
    val minutes: Long?,
    val label: String,
    val summary: String,
)

data class Forecast(
    val city: String,
    val now: OffsetDateTime,
    val timezone: String,
    val sun: Change,
    val rain: Change,
    val horizon: OffsetDateTime?,
    val sources: List<String>,
    val fetchedAt: OffsetDateTime = OffsetDateTime.now(),
)
