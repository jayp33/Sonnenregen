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
    @Deprecated("Legacy stored value; rendered as SUN")
    CLEAR_NIGHT,
    @Deprecated("Legacy stored value; rendered as CLOUDY")
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
            kind = classify(slot),
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

private fun classify(slot: Slot): HourKind {
    // Day and night variants of the same condition share a colour, so the grid
    // reads purely as weather at a glance. Sunrise/sunset stay in the text line.
    if (isRainSymbol(slot.symbol)) return rainKind(slot.symbol)

    return when (slot.symbol.take(2)) {
        "so", "mo" -> HourKind.SUN // sunny / clear night
        "wb", "mb" -> HourKind.PARTLY_SUN // partly sunny / partly cloudy night
        "bw", "mw" -> HourKind.CLOUDY
        else -> HourKind.OVERCAST // bd, md, fog, ...
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
