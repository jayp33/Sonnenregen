package de.jayp33.sonnenregen

import de.jayp33.sonnenregen.data.AstroDayDto
import de.jayp33.sonnenregen.data.AstroDto
import de.jayp33.sonnenregen.data.AstroSunDto
import de.jayp33.sonnenregen.domain.Daylight
import de.jayp33.sonnenregen.domain.Slot
import de.jayp33.sonnenregen.domain.SlotSource
import de.jayp33.sonnenregen.domain.applyDaylight
import de.jayp33.sonnenregen.domain.daylightWindows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.OffsetDateTime

class DaylightTest {
    @Test
    fun splits_slots_at_sunrise_and_sunset() {
        val start = OffsetDateTime.parse("2026-11-21T07:00:00+01:00")
        val slot = Slot(start, Duration.ofHours(2), "so____", SlotSource.HOURLY)
        val windows = listOf(
            Daylight(start.plusMinutes(38), start.plusMinutes(60)),
        )

        val result = applyDaylight(listOf(slot), windows)

        assertEquals(3, result.size)
        assertEquals(Duration.ofMinutes(38), result[0].duration)
        assertEquals(Duration.ofMinutes(22), result[1].duration)
        assertEquals(Duration.ofHours(1), result[2].duration)
        assertFalse(result[0].daylight!!)
        assertTrue(result[1].daylight!!)
        assertFalse(result[2].daylight!!)
        assertFalse(result[0].sunny)
        assertTrue(result[1].sunny)
        assertFalse(result[2].sunny)
    }

    @Test
    fun night_symbol_becomes_day_symbol_at_sunrise() {
        val start = OffsetDateTime.parse("2026-11-21T07:00:00+01:00")
        val slot = Slot(start, Duration.ofHours(1), "mo____", SlotSource.HOURLY, night = true)
        val windows = listOf(Daylight(start.plusMinutes(38), start.plusHours(2)))

        val (before, after) = applyDaylight(listOf(slot), windows)

        assertEquals("mo____", before.symbol)
        assertFalse(before.sunny)
        assertEquals("so____", after.symbol)
        assertTrue(after.sunny)
        assertNull(after.night)
    }

    @Test
    fun no_windows_leave_the_timeline_untouched() {
        val slot = Slot(
            OffsetDateTime.parse("2026-11-21T07:00:00+01:00"),
            Duration.ofHours(1),
            "so____",
            SlotSource.HOURLY,
        )

        val result = applyDaylight(listOf(slot), emptyList())

        assertEquals(listOf(slot), result)
        assertTrue(result[0].sunny)
    }

    @Test
    fun ignores_polar_days_and_malformed_times() {
        val astro = AstroDto(
            days = listOf(
                astroDay("2026-11-21T07:38:00+01:00", "2026-11-21T16:05:00+01:00"),
                AstroDayDto("2026-11-22T00:00:00+01:00", AstroSunDto("polar_night")),
                astroDay("garbage", "also garbage"),
            ),
        )

        val windows = daylightWindows(astro)

        assertEquals(1, windows.size)
        assertEquals(
            OffsetDateTime.parse("2026-11-21T07:38:00+01:00"),
            windows[0].rise,
        )
    }

    private fun astroDay(rise: String, set: String) = AstroDayDto(
        date = "2026-11-21T00:00:00+01:00",
        sun = AstroSunDto("sunrise_and_sunset", rise, set),
    )
}
