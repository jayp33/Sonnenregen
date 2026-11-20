package de.jayp33.sonnenregen

import de.jayp33.sonnenregen.data.ForecastParser
import de.jayp33.sonnenregen.domain.ForecastAnalyzer
import de.jayp33.sonnenregen.domain.isRainSymbol
import de.jayp33.sonnenregen.domain.isSunSymbol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime

class ForecastAnalyzerTest {
    @Test
    fun reads_real_forecast() {
        val page = TestData.page()
        val forecast = ForecastAnalyzer.analyze("berlin", page, page)

        assertEquals("berlin", forecast.city)
        assertFalse(forecast.sun.currently)
        assertEquals("starts", forecast.sun.event!!.name.lowercase())
        assertEquals(OffsetDateTime.parse("2026-11-21T08:00:00+01:00"), forecast.sun.at)

        assertFalse(forecast.rain.currently)
        assertEquals("starts", forecast.rain.event!!.name.lowercase())
        assertEquals(OffsetDateTime.parse("2026-11-21T20:00:00+01:00"), forecast.rain.at)
        assertEquals(48, forecast.hours.size)
    }

    @Test
    fun uses_exact_sunrise_and_reports_next_sun_times() {
        val morning = TestData.sunnyMorningShortcast
        val forecast = ForecastAnalyzer.analyze(
            "berlin",
            TestData.pageWith(morning, TestData.astroJson),
            TestData.pageWith(morning),
        )

        assertEquals(OffsetDateTime.parse("2026-11-21T07:38:00+01:00"), forecast.sun.at)
        assertEquals(OffsetDateTime.parse("2026-11-21T07:38:00+01:00"), forecast.sunrise)
        assertEquals(OffsetDateTime.parse("2026-11-21T16:05:00+01:00"), forecast.sunset)
    }

    @Test
    fun clear_night_slot_starts_sunshine_at_sunrise() {
        val forecast = ForecastAnalyzer.analyze(
            "berlin",
            TestData.page(TestData.astroJson),
            TestData.page(),
        )

        assertEquals(OffsetDateTime.parse("2026-11-21T07:38:00+01:00"), forecast.sun.at)
    }

    @Test
    fun cloudy_night_slot_still_delays_sunshine_until_a_sunny_slot() {
        val morning = TestData.sunnyMorningShortcast.replace(
            """"date":"2026-11-21T07:00:00+01:00","symbol":"mo____"""",
            """"date":"2026-11-21T07:00:00+01:00","symbol":"mw____"""",
        )
        val forecast = ForecastAnalyzer.analyze(
            "berlin",
            TestData.pageWith(morning, TestData.astroJson),
            TestData.pageWith(morning),
        )

        assertEquals(OffsetDateTime.parse("2026-11-21T08:00:00+01:00"), forecast.sun.at)
    }

    @Test
    fun sunset_caps_sunshine_inside_a_sunny_slot() {
        val astro = TestData.astroJson.replace(
            "2026-11-21T16:05:00+01:00",
            "2026-11-21T15:52:00+01:00",
        )
        val forecast = ForecastAnalyzer.analyze(
            "berlin",
            TestData.page(astro),
            TestData.page(),
            now = OffsetDateTime.parse("2026-11-21T15:00:00+01:00"),
        )

        assertEquals(true, forecast.sun.currently)
        assertEquals("ends", forecast.sun.event!!.name.lowercase())
        assertEquals(OffsetDateTime.parse("2026-11-21T15:52:00+01:00"), forecast.sun.at)
    }

    @Test
    fun timeline_is_continuous() {
        val shortcast = ForecastParser.parsePage(TestData.page())
        val (hours, nowcast, current) = ForecastAnalyzer.slotsFrom(shortcast)
        check(current != null)

        val timeline = ForecastAnalyzer.buildTimeline(current.start, hours, nowcast, current)
        for (index in 1 until timeline.size) {
            assertEquals(timeline[index - 1].end, timeline[index].start)
        }
    }

    @Test
    fun symbol_classification() {
        assertTrue(isSunSymbol("so____"))
        assertTrue(isSunSymbol("wb____"))
        assertFalse(isSunSymbol("bw____"))

        assertTrue(isRainSymbol("mdr1__"))
        assertTrue(isRainSymbol("mds1__"))
        assertTrue(isRainSymbol("bdr2__"))
        assertFalse(isRainSymbol("mdsn1_"))
        assertFalse(isRainSymbol("so____"))
    }
}
