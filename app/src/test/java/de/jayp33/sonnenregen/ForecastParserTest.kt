package de.jayp33.sonnenregen

import de.jayp33.sonnenregen.data.ForecastParser
import de.jayp33.sonnenregen.data.ParseException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ForecastParserTest {
    @Test
    fun reads_real_payload() {
        val shortcast = ForecastParser.parsePage(TestData.page())

        assertEquals(49, shortcast.hours.size)
        assertEquals(7, shortcast.nowcastTrend!!.items.size)
        assertEquals("mo____", shortcast.current!!.symbol)
    }

    @Test
    fun rejects_pages_without_state() {
        assertThrows(ParseException::class.java) {
            ForecastParser.parsePage("<html></html>")
        }
    }

    @Test
    fun reads_astro_sunrise_and_sunset() {
        val data = ForecastParser.parsePageData(TestData.page(TestData.astroJson))

        assertEquals(3, data.astro!!.days.size)
        assertEquals("2026-11-21T07:38:00+01:00", data.astro.days[1].sun!!.rise)
        assertEquals("polar_night", data.astro.days[2].sun!!.courseType)
    }

    @Test
    fun missing_or_broken_astro_does_not_break_the_forecast() {
        assertNull(ForecastParser.parsePageData(TestData.page()).astro)

        val data = ForecastParser.parsePageData(TestData.page("""{"days":"not a list"}"""))
        assertNotNull(data.shortcast.current)
        assertNull(data.astro)
    }
}
