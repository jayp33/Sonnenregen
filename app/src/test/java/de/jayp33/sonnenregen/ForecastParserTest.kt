package de.jayp33.sonnenregen

import de.jayp33.sonnenregen.data.ForecastParser
import de.jayp33.sonnenregen.data.ParseException
import org.junit.Assert.assertEquals
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
}
