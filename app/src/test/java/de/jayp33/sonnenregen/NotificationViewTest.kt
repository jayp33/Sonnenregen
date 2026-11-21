package de.jayp33.sonnenregen

import de.jayp33.sonnenregen.data.CityStatus
import de.jayp33.sonnenregen.domain.displayCity
import de.jayp33.sonnenregen.notification.NotificationViewBuilder
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationViewTest {
    @Test
    fun one_city_uses_the_city_as_header() {
        val view = build(listOf(status("berlin", "Sun in 2 h, no rain")))

        assertEquals("Berlin", view.title)
        assertEquals("Sun in 2 h, no rain", view.text)
        assertEquals(true, view.useBigText)
        assertEquals(emptyList<String>(), view.lines)
    }

    @Test
    fun one_stale_city_shows_unavailable_as_its_body() {
        val view = build(listOf(status("berlin", "Sun in 2 h, no rain", stale = true)))

        assertEquals("Berlin", view.title)
        assertEquals("Weather unavailable", view.text)
        assertEquals(true, view.useBigText)
    }

    @Test
    fun single_city_summary_uses_wrapping_text() {
        val summary = "Sonne in 2 Std. 10 Min., Regen in 5 Std. 30 Min."
        val view = build(listOf(status("berlin", summary)))

        assertEquals("Berlin", view.title)
        assertEquals(summary, view.text)
        assertEquals(true, view.useBigText)
        assertEquals(emptyList<String>(), view.lines)
    }

    @Test
    fun two_cities_use_wrapping_text_with_the_app_name_as_header() {
        val view = build(
            listOf(
                status("berlin", "Sun in 2 h, no rain"),
                status("hamburg", "Rain in 30 min, sun in 5 h"),
            ),
        )

        assertEquals("Sonnenregen", view.title)
        assertEquals("Berlin: Sun in 2 h, no rain", view.text)
        assertEquals(true, view.useBigText)
        assertEquals(
            listOf(
                "Berlin: Sun in 2 h, no rain",
                "Hamburg: Rain in 30 min, sun in 5 h",
            ),
            view.lines,
        )
    }

    @Test
    fun long_city_names_keep_the_whole_summary_line() {
        val view = build(
            listOf(
                status("frankfurt-am-main", "Sun in 2 h, no rain"),
                status("berlin", "Rain in 30 min"),
            ),
        )

        assertEquals(
            listOf(
                "Frankfurt-am-main: Sun in 2 h, no rain",
                "Berlin: Rain in 30 min",
            ),
            view.lines,
        )
        assertEquals(
            "Frankfurt-am-main: Sun in 2 h, no rain\nBerlin: Rain in 30 min",
            view.lines.joinToString("\n"),
        )
    }

    @Test
    fun uses_the_real_place_name_from_wetteronline() {
        val view = build(
            listOf(
                status("frankfurt-oder", "Sun in 2 h", displayName = "Frankfurt (Oder)"),
                status(
                    "michelbach-an-der-bilz/gschlachtenbretzingen",
                    "Rain in 30 min",
                    displayName = "Gschlachtenbretzingen",
                ),
            ),
        )

        assertEquals("Sonnenregen", view.title)
        assertEquals(
            listOf(
                "Frankfurt (Oder): Sun in 2 h",
                "Gschlachtenbretzingen: Rain in 30 min",
            ),
            view.lines,
        )
    }

    @Test
    fun no_cities_shows_the_empty_state() {
        val view = build(emptyList())

        assertEquals("Sonnenregen", view.title)
        assertEquals("No cities configured", view.text)
        assertEquals(true, view.useBigText)
        assertEquals(listOf("No cities configured"), view.lines)
    }

    private fun build(statuses: List<CityStatus>) = NotificationViewBuilder.build(
        statuses = statuses,
        appName = "Sonnenregen",
        noCitiesText = "No cities configured",
        textFor = { if (it.stale) "Weather unavailable" else it.compactSummary },
        lineFor = { city, text -> "$city: $text" },
    )

    private fun status(
        city: String,
        compactSummary: String,
        stale: Boolean = false,
        displayName: String = displayCity(city),
    ) = CityStatus(
        city = city,
        displayName = displayName,
        compactSummary = compactSummary,
        fetchedAtMillis = 20L,
        stale = stale,
    )
}
