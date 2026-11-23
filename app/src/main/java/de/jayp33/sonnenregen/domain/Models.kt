package de.jayp33.sonnenregen.domain

import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.OffsetDateTime

enum class SlotSource { CURRENT, NOWCAST, HOURLY, INTERPOLATED }

/** Sunrise/sunset markers shown on the matching cell of the 48-hour grid. */
@Serializable
enum class SunEventKind { SUNRISE, SUNSET }

@Serializable
data class SunEvent(val kind: SunEventKind, val atMillis: Long)

enum class Event { STARTS, ENDS }

enum class Condition { SUN, RAIN, SNOW }

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

    val snowing: Boolean get() = isSnowSymbol(symbol)
}

fun isSunSymbol(symbol: String): Boolean =
    symbol.startsWith("so") || symbol.startsWith("wb")

fun isRainSymbol(symbol: String): Boolean {
    val detail = symbol.drop(2)
    if ('n' in detail) return false // snow
    return detail.any { it == 'r' || it == 's' || it == 'g' }
}

/** Snow is precipitation too, but gets its own colour instead of a rain blue. */
fun isSnowSymbol(symbol: String): Boolean = 'n' in symbol.drop(2)

data class Change(
    val currently: Boolean,
    val event: Event?,
    val at: OffsetDateTime?,
    val minutes: Long?,
    val condition: Condition,
)

/** A continuous span in the timeline, used for condition windows. */
data class TimeWindow(val start: OffsetDateTime, val end: OffsetDateTime)

/**
 * Rain that starts again after the current/first rain window inside the
 * provider's 90-minute nowcast. Null when there is no such second window.
 */
data class RainOutlook(
    val againInMinutes: Long? = null,
)

data class Forecast(
    val city: String,
    /** Real place name from WetterOnline; the city slug stays the URL key. */
    val displayName: String,
    val now: OffsetDateTime,
    val timezone: String,
    val sun: Change,
    val rain: Change,
    /** Snow onset/end, mirroring [rain]; null in forecasts built without it. */
    val snow: Change? = null,
    val rainOutlook: RainOutlook = RainOutlook(),
    val horizon: OffsetDateTime?,
    val sources: List<String>,
    val fetchedAt: OffsetDateTime = OffsetDateTime.now(),
    val sunrise: OffsetDateTime? = null,
    val sunset: OffsetDateTime? = null,
    val hours: List<HourCell> = emptyList(),
    /** Quarter-hour cells for the 90-minute window, used by the extra grid row. */
    val nowcastCells: List<HourCell> = emptyList(),
    /** Sunrise/sunset times inside the 48-hour grid; old forecasts have none. */
    val sunEvents: List<SunEvent> = emptyList(),
)
