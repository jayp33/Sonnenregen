package de.jayp33.sonnenregen.domain

import de.jayp33.sonnenregen.data.AstroDto
import java.time.Duration
import java.time.OffsetDateTime

data class Daylight(val rise: OffsetDateTime, val set: OffsetDateTime)

/** Only days with both rise and set times (polar day/night has neither). */
fun daylightWindows(astro: AstroDto?): List<Daylight> =
    astro?.days.orEmpty().mapNotNull { day ->
        val rise = parseTimestampOrNull(day.sun?.rise) ?: return@mapNotNull null
        val set = parseTimestampOrNull(day.sun?.set) ?: return@mapNotNull null
        if (set.isAfter(rise)) Daylight(rise, set) else null
    }.sortedBy { it.rise }

/**
 * Split forecast slots at sunrise/sunset and mark which parts are in daylight.
 * Symbols still decide whether the sun actually shines inside daylight.
 */
fun applyDaylight(timeline: List<Slot>, windows: List<Daylight>): List<Slot> {
    if (windows.isEmpty() || timeline.isEmpty()) return timeline

    val cuts = windows.flatMap { listOf(it.rise, it.set) }
        .filter { it.isAfter(timeline.first().start) && it.isBefore(timeline.last().end) }
        .sorted()

    val split = mutableListOf<Slot>()
    for (slot in timeline) {
        var start = slot.start
        for (cut in cuts.filter { it.isAfter(start) && it.isBefore(slot.end) }) {
            split.add(slot.copy(start = start, duration = Duration.between(start, cut)))
            start = cut
        }
        split.add(slot.copy(start = start, duration = Duration.between(start, slot.end)))
    }

    return split.map { slot ->
        val inDaylight = windows.any { window ->
            !slot.start.isBefore(window.rise) && slot.start.isBefore(window.set)
        }
        slot.copy(daylight = inDaylight)
    }
}
