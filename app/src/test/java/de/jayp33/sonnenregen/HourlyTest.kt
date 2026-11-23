package de.jayp33.sonnenregen

import de.jayp33.sonnenregen.domain.Daylight
import de.jayp33.sonnenregen.domain.HourCell
import de.jayp33.sonnenregen.domain.HourKind
import de.jayp33.sonnenregen.domain.Slot
import de.jayp33.sonnenregen.domain.SlotSource
import de.jayp33.sonnenregen.domain.currentOrFutureHours
import de.jayp33.sonnenregen.domain.currentOrFutureNowcast
import de.jayp33.sonnenregen.domain.groupHoursByDay
import de.jayp33.sonnenregen.domain.hourlyCells
import de.jayp33.sonnenregen.domain.isRain
import de.jayp33.sonnenregen.domain.isSnow
import de.jayp33.sonnenregen.domain.isSnowSymbol
import de.jayp33.sonnenregen.domain.isSun
import de.jayp33.sonnenregen.domain.nowcastCells
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun night_symbol_at_sunrise_gets_the_day_colour() {
        val daylight = listOf(
            Daylight(
                at("2026-11-21T07:41:00+01:00"),
                at("2026-11-21T16:32:00+01:00"),
            ),
        )
        val hours = listOf(
            hour("2026-11-21T07:00:00+01:00", "mb____"),
            hour("2026-11-21T08:00:00+01:00", "bw____"),
            hour("2026-11-21T16:00:00+01:00", "so____"),
            hour("2026-11-21T17:00:00+01:00", "md____"),
        )
        val now = at("2026-11-21T07:00:00+01:00")

        assertEquals(HourKind.CLOUDY, hourlyCells(now, hours).first().kind)

        val cells = hourlyCells(now, hours, daylight = daylight)

        assertEquals(HourKind.PARTLY_SUN, cells[0].kind) // mb -> wb at sunrise
        assertEquals(HourKind.CLOUDY, cells[1].kind) // bw stays cloudy
        assertEquals(HourKind.SUN, cells[2].kind) // so stays bright at sunset
        assertEquals(HourKind.CLOUDY, cells[3].kind) // md stays gray after sunset
    }

    @Test
    fun only_day_sun_symbols_count_as_sun() {
        assertTrue(HourKind.SUN.isSun())
        assertTrue(HourKind.PARTLY_SUN.isSun())
        assertFalse(HourKind.CLOUDY.isSun())
        assertFalse(HourKind.RAIN.isSun())
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
    fun snow_gets_its_own_shades_in_both_grids() {
        val now = at("2026-11-21T10:00:00+01:00")
        val hourly = hourlyCells(
            now = now,
            hours = listOf(
                hour("2026-11-21T10:00:00+01:00", "mdsn1_"),
                hour("2026-11-21T11:00:00+01:00", "bdsn2_"),
                hour("2026-11-21T12:00:00+01:00", "bdsn3_"),
            ),
        )
        val nowcast = nowcastCells(
            now = now,
            slots = listOf(
                Slot(now, Duration.ofMinutes(15), "bwsn1_", SlotSource.NOWCAST),
                Slot(now.plusMinutes(15), Duration.ofMinutes(15), "bwsn3_", SlotSource.NOWCAST),
                Slot(now.plusMinutes(30), Duration.ofMinutes(15), "mo____", SlotSource.NOWCAST),
            ),
        )

        assertTrue(isSnowSymbol("mdsn1_"))
        assertTrue(isSnowSymbol("bdsn2_"))
        assertEquals(
            listOf(HourKind.LIGHT_SNOW, HourKind.SNOW, HourKind.HEAVY_SNOW),
            hourly.map { it.kind },
        )
        assertEquals(
            listOf(HourKind.LIGHT_SNOW, HourKind.HEAVY_SNOW, HourKind.CLOUDY),
            nowcast.map { it.kind },
        )
        assertTrue(nowcast.first().kind.isSnow())
        assertFalse(nowcast.first().kind.isRain())
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

    @Test
    fun nowcast_cells_cover_six_quarter_hours_from_now() {
        val slots = nowcastSlots("2026-11-21T21:00:00+01:00", 7, rainAt = 1)
        val now = at("2026-11-21T21:15:00+01:00")

        val cells = nowcastCells(now, slots)

        assertEquals(6, cells.size)
        assertEquals(now.toInstant().toEpochMilli(), cells.first().startEpochMillis)
        assertEquals(HourKind.LIGHT_RAIN, cells.first().kind)
        assertEquals(HourKind.CLOUDY, cells.last().kind)
        assertEquals("+01:00", cells.first().zoneId)
        assertTrue(cells.any { it.kind.isRain() })
    }

    @Test
    fun elapsed_nowcast_cells_are_dropped_but_the_current_one_stays() {
        val now = at("2026-11-21T21:15:00+01:00").toInstant().toEpochMilli()
        val old = HourCell(now - 30 * 60_000L, HourKind.RAIN)
        val justEnded = HourCell(now - 15 * 60_000L, HourKind.SUN)
        val current = HourCell(now, HourKind.RAIN)
        val future = HourCell(now + 15 * 60_000L, HourKind.SUN)
        val cells = listOf(old, justEnded, current, future)

        assertEquals(listOf(current, future), cells.currentOrFutureNowcast(now))
    }

    @Test
    fun elapsed_hourly_cells_are_dropped_but_the_current_one_stays() {
        val now = at("2026-11-21T10:30:00+01:00").toInstant().toEpochMilli()
        val old = HourCell(at("2026-11-21T08:00:00+01:00").toInstant().toEpochMilli(), HourKind.SUN)
        val justEnded = HourCell(at("2026-11-21T09:00:00+01:00").toInstant().toEpochMilli(), HourKind.RAIN)
        val current = HourCell(at("2026-11-21T10:00:00+01:00").toInstant().toEpochMilli(), HourKind.SUN)
        val future = HourCell(at("2026-11-21T11:00:00+01:00").toInstant().toEpochMilli(), HourKind.RAIN)
        val cells = listOf(old, justEnded, current, future)

        assertEquals(listOf(current, future), cells.currentOrFutureHours(now))
    }

    @Test
    fun nowcast_without_rain_has_no_rain_cells() {
        val slots = nowcastSlots("2026-11-21T21:15:00+01:00", 6, rainAt = -1)

        val cells = nowcastCells(at("2026-11-21T21:15:00+01:00"), slots)

        assertEquals(6, cells.size)
        assertFalse(cells.any { it.kind.isRain() })
    }

    private fun nowcastSlots(start: String, count: Int, rainAt: Int): List<Slot> {
        val first = at(start)
        return (0 until count).map { index ->
            Slot(
                start = first.plusMinutes(15L * index),
                duration = Duration.ofMinutes(15),
                symbol = if (index == rainAt) "mdr1__" else "mo____",
                source = SlotSource.NOWCAST,
            )
        }
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
