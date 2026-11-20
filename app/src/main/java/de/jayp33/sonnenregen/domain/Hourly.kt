package de.jayp33.sonnenregen.domain

import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Colour-only weather state for one hour of the overview strip. */
@Serializable
enum class HourKind {
    SUN,
    PARTLY_SUN,
    CLOUDY,
    OVERCAST,
    CLEAR_NIGHT,
    CLOUDY_NIGHT,
    LIGHT_RAIN,
    RAIN,
    HEAVY_RAIN,
}

@Serializable
data class HourCell(
    val startEpochMillis: Long,
    val kind: HourKind,
)

/**
 * Build 48 one-hour cells starting at the current hour. The provider usually
 * starts its hourly grid at the next full hour, so the current hour is taken
 * from the `current` observation when needed.
 */
fun hourlyCells(
    now: OffsetDateTime,
    hours: List<Slot>,
    windows: List<Daylight>,
    current: Slot? = null,
): List<HourCell> {
    val start = now.truncatedTo(ChronoUnit.HOURS)
    val slots = mutableListOf<Slot>()
    val nextHour = hours.firstOrNull { !it.start.isBefore(start) }

    if (nextHour?.start != start) {
        val source = current?.takeIf { it.start < (nextHour?.start ?: start.plusHours(1)) } ?: nextHour
        if (source != null) {
            slots.add(source.copy(start = start, duration = Duration.ofHours(1)))
        }
    }
    slots.addAll(hours.filter { !it.start.isBefore(start) }.take(48 - slots.size))

    return slots.take(48).map { slot ->
        HourCell(
            startEpochMillis = slot.start.toInstant().toEpochMilli(),
            kind = classify(slot, windows),
        )
    }
}

/** Group cells by calendar day in the forecast's timezone, then by hour. */
fun groupHoursByDay(
    hours: List<HourCell>,
    zone: ZoneId = ZoneId.of(TIMEZONE),
): Map<LocalDate, Map<Int, HourCell>> {
    val result = LinkedHashMap<LocalDate, MutableMap<Int, HourCell>>()
    hours.forEach { cell ->
        val time = Instant.ofEpochMilli(cell.startEpochMillis).atZone(zone)
        result.getOrPut(time.toLocalDate()) { LinkedHashMap() }[time.hour] = cell
    }
    return result
}

private fun classify(slot: Slot, windows: List<Daylight>): HourKind {
    // An hour that only partly overlaps daylight still gets the day symbol, so
    // the 07:00 cell shows sun when sunrise is 07:38.
    val inDaylight = windows.any { slot.start < it.set && slot.end > it.rise }
    val symbol = if (inDaylight) slot.symbol.asDaySymbol() else slot.symbol

    if (isRainSymbol(symbol)) return rainKind(symbol)

    if (inDaylight && isSunSymbol(symbol)) {
        return if (symbol.startsWith("so")) HourKind.SUN else HourKind.PARTLY_SUN
    }

    return if (inDaylight) {
        if (symbol.startsWith("bw")) HourKind.CLOUDY else HourKind.OVERCAST
    } else {
        if (symbol.startsWith("mo")) HourKind.CLEAR_NIGHT else HourKind.CLOUDY_NIGHT
    }
}

private fun rainKind(symbol: String): HourKind {
    val detail = symbol.drop(2)
    val level = detail.firstOrNull { it.isDigit() }?.digitToInt()
    return when {
        'g' in detail || (level ?: 0) >= 3 -> HourKind.HEAVY_RAIN
        level == 2 -> HourKind.RAIN
        else -> HourKind.LIGHT_RAIN
    }
}
