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
    /** Offset/zone from WetterOnline's timestamp, e.g. "-05:00" or "Europe/Berlin". */
    val zoneId: String = TIMEZONE,
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
    daylight: List<Daylight> = emptyList(),
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
        // WetterOnline labels a whole hour with a night symbol when it starts
        // before sunrise. If sunrise/sunset falls inside it, use the matching
        // day symbol for the colour grid too, so the cell agrees with the
        // sun text. Hours fully inside daylight/night keep their raw symbol.
        val overviewSlot = if (daylight.any { it.crosses(slot) }) {
            slot.copy(symbol = slot.symbol.asDaySymbol())
        } else {
            slot
        }
        HourCell(
            startEpochMillis = slot.start.toInstant().toEpochMilli(),
            kind = classify(overviewSlot),
            zoneId = slot.start.offset.id,
        )
    }
}

/**
 * Build up to six 15-minute cells from the provider's nowcast slots. These are
 * the raw quarter-hour items, not the stretched slots of the merged timeline.
 */
fun nowcastCells(now: OffsetDateTime, slots: List<Slot>, limit: Int = 6): List<HourCell> =
    slots.filter { it.end.isAfter(now) }.take(limit).map { slot ->
        HourCell(
            startEpochMillis = slot.start.toInstant().toEpochMilli(),
            kind = classify(slot),
            zoneId = slot.start.offset.id,
        )
    }

/** Group cells by calendar day/hour in each cell's own offset, then by hour. */
fun groupHoursByDay(hours: List<HourCell>): Map<LocalDate, Map<Int, HourCell>> {
    val fallback = ZoneId.of(TIMEZONE)
    val result = LinkedHashMap<LocalDate, MutableMap<Int, HourCell>>()
    hours.forEach { cell ->
        val zone = try {
            ZoneId.of(cell.zoneId)
        } catch (_: Exception) {
            fallback
        }
        val time = Instant.ofEpochMilli(cell.startEpochMillis).atZone(zone)
        result.getOrPut(time.toLocalDate()) { LinkedHashMap() }[time.hour] = cell
    }
    return result
}

internal fun HourKind.isRain(): Boolean =
    this == HourKind.LIGHT_RAIN || this == HourKind.RAIN || this == HourKind.HEAVY_RAIN

private fun classify(slot: Slot): HourKind {
    // Night symbols (m*) never get a sun colour: no sun before sunrise or after
    // sunset. Only WetterOnline's actual day sun symbols are yellow.
    if (isRainSymbol(slot.symbol)) return rainKind(slot.symbol)

    return when (slot.symbol.take(2)) {
        "so" -> HourKind.SUN
        "wb" -> HourKind.PARTLY_SUN
        else -> HourKind.CLOUDY // bw, bd and every night symbol: mo, mb, mw, md
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
