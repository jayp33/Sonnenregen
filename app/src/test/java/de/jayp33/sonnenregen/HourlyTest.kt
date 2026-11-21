package de.jayp33.sonnenregen

import de.jayp33.sonnenregen.domain.HourCell
import de.jayp33.sonnenregen.domain.HourKind
import de.jayp33.sonnenregen.domain.Slot
import de.jayp33.sonnenregen.domain.SlotSource
import de.jayp33.sonnenregen.domain.groupHoursByDay
import de.jayp33.sonnenregen.domain.hourlyCells
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.OffsetDateTime

class HourlyTest {
    @Test
    fun builds_48_cells_from_the_current_hour() {
        val cells = hourlyCells(
            now = at("2026-11-20T21:26:00+01:00"),
            hours = hours("2026-11-20T22:00:00+01:00", 49, "mo____"),
            current = Slot(
                at("2026-11-20T21:26:00+01:00"),
                Duration.ofMinutes(15),
                "mo____",
                SlotSource.CURRENT,
            ),
        )

        assertEquals(48, cells.size)
        assertEquals(at("2026-11-20T21:00:00+01:00").toInstant().toEpochMilli(), cells.first().startEpochMillis)
        assertEquals(at("2026-11-22T20:00:00+01:00").toInstant().toEpochMilli(), cells.last().startEpochMillis)
        assertEquals("+01:00", cells.first().zoneId)
    }

    @Test
    fun night_symbols_are_never_sunny() {
        val cells = hourlyCells(
            now = at("2026-11-21T06:30:00+01:00"),
            hours = listOf(
                hour("2026-11-21T07:00:00+01:00", "mo____"),
                hour("2026-11-21T08:00:00+01:00", "so____"),
                hour("2026-11-21T09:00:00+01:00", "mb____"),
                hour("2026-11-21T10:00:00+01:00", "wb____"),
                hour("2026-11-21T11:00:00+01:00", "mw____"),
                hour("2026-11-21T12:00:00+01:00", "bw____"),
                hour("2026-11-21T13:00:00+01:00", "md____"),
                hour("2026-11-21T14:00:00+01:00", "bd____"),
            ),
            current = Slot(
                at("2026-11-21T06:30:00+01:00"),
                Duration.ofMinutes(15),
                "mo____",
                SlotSource.CURRENT,
            ),
        )

        assertEquals(
            listOf(
                HourKind.CLOUDY, // 06:00 current, clear night
                HourKind.CLOUDY, // mo
                HourKind.SUN, // so
                HourKind.CLOUDY, // mb, partly cloudy night
                HourKind.PARTLY_SUN, // wb, partly sunny day
                HourKind.CLOUDY, // mw
                HourKind.CLOUDY, // bw
                HourKind.CLOUDY, // md
                HourKind.CLOUDY, // bd
            ),
            cells.map { it.kind },
        )
    }

    @Test
    fun rain_intensity_selects_the_blue_shade() {
        val cells = hourlyCells(
            now = at("2026-11-21T10:00:00+01:00"),
            hours = listOf(
                hour("2026-11-21T10:00:00+01:00", "mdr1__"),
                hour("2026-11-21T11:00:00+01:00", "bwr2__"),
                hour("2026-11-21T12:00:00+01:00", "mds3__"),
            ),
        )

        assertEquals(
            listOf(HourKind.LIGHT_RAIN, HourKind.RAIN, HourKind.HEAVY_RAIN),
            cells.map { it.kind },
        )
    }

    @Test
    fun an_evening_window_groups_into_three_day_rows() {
        val cells = cellsFrom("2026-11-20T21:00:00+01:00", 48)

        val grouped = groupHoursByDay(cells)

        assertEquals(3, grouped.size)
        assertEquals(listOf(21, 22, 23), grouped.values.first().keys.toList())
        assertEquals((0..23).toList(), grouped.values.elementAt(1).keys.toList())
        assertEquals((0..20).toList(), grouped.values.elementAt(2).keys.toList())
    }

    @Test
    fun a_midnight_window_groups_into_two_day_rows() {
        val grouped = groupHoursByDay(cellsFrom("2026-11-21T00:00:00+01:00", 48))

        assertEquals(2, grouped.size)
        assertEquals((0..23).toList(), grouped.values.first().keys.toList())
        assertEquals((0..23).toList(), grouped.values.last().keys.toList())
    }

    @Test
    fun grouping_uses_each_cell_offset_not_a_global_zone() {
        val cells = listOf(
            HourCell(at("2026-11-21T00:00:00-05:00").toInstant().toEpochMilli(), HourKind.SUN, "-05:00"),
            HourCell(at("2026-11-21T01:00:00-05:00").toInstant().toEpochMilli(), HourKind.SUN, "-05:00"),
            HourCell(at("2026-11-21T11:00:00+01:00").toInstant().toEpochMilli(), HourKind.SUN, "+01:00"),
        )

        val grouped = groupHoursByDay(cells)

        assertEquals(1, grouped.size)
        assertEquals(listOf(0, 1, 11), grouped.values.first().keys.toList())
    }

    private fun cellsFrom(start: String, count: Int): List<HourCell> {
        val first = at(start)
        return (0 until count).map { index ->
            HourCell(first.plusHours(index.toLong()).toInstant().toEpochMilli(), HourKind.SUN)
        }
    }

    private fun hours(start: String, count: Int, symbol: String): List<Slot> {
        val first = at(start)
        return (0 until count).map { index -> hour(first.plusHours(index.toLong()), symbol) }
    }

    private fun hour(start: String, symbol: String): Slot = hour(at(start), symbol)

    private fun hour(start: OffsetDateTime, symbol: String): Slot = Slot(
        start = start,
        duration = Duration.ofHours(1),
        symbol = symbol,
        source = SlotSource.HOURLY,
    )

    private fun at(value: String): OffsetDateTime = OffsetDateTime.parse(value)
}
