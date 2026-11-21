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
    fun reads_the_real_place_name_from_the_heading() {
        val page = "<h1> Wetter Gschlachtenbretzingen &nbsp;</h1>" + TestData.page()

        assertEquals("Gschlachtenbretzingen", ForecastParser.parsePageData(page).placeName)
    }

    @Test
    fun keeps_official_parentheses_in_place_names() {
        val page = "<h1> Wetter Frankfurt (Oder) &nbsp;</h1>" + TestData.page()

        assertEquals("Frankfurt (Oder)", ForecastParser.parsePageData(page).placeName)
    }

    @Test
    fun falls_back_to_the_page_title_for_the_place_name() {
        val page = "<title>14-Tage-Wetter Frankfurt (Oder) - WetterOnline</title>" + TestData.page()

        assertEquals("Frankfurt (Oder)", ForecastParser.parsePageData(page).placeName)
    }

    @Test
    fun missing_or_broken_astro_does_not_break_the_forecast() {
        assertNull(ForecastParser.parsePageData(TestData.page()).astro)

        val data = ForecastParser.parsePageData(TestData.page("""{"days":"not a list"}"""))
        assertNotNull(data.shortcast.current)
        assertNull(data.astro)
    }
}
