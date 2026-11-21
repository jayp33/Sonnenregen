package de.jayp33.sonnenregen.domain

import java.time.Duration
import java.time.OffsetDateTime

enum class SlotSource { CURRENT, NOWCAST, HOURLY, INTERPOLATED }

enum class Event { STARTS, ENDS }

enum class Condition { SUN, RAIN }

data class Slot(
    val start: OffsetDateTime,
    val duration: Duration,
    val symbol: String,
    val source: SlotSource,
    val solarElevation: Double? = null,
    val night: Boolean? = null,
    /** Exact sunrise/sunset override; null when no astro data is available. */
    val daylight: Boolean? = null,
) {
    val end: OffsetDateTime get() = start.plus(duration)

    val sunny: Boolean
        get() {
            if (daylight == false) return false
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
    val condition: Condition,
)

data class Forecast(
    val city: String,
    /** Real place name from WetterOnline; the city slug stays the URL key. */
    val displayName: String,
    val now: OffsetDateTime,
    val timezone: String,
    val sun: Change,
    val rain: Change,
    val horizon: OffsetDateTime?,
    val sources: List<String>,
    val fetchedAt: OffsetDateTime = OffsetDateTime.now(),
    val sunrise: OffsetDateTime? = null,
    val sunset: OffsetDateTime? = null,
    val hours: List<HourCell> = emptyList(),
)
