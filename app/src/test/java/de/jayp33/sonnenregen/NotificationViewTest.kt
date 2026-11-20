package de.jayp33.sonnenregen

import de.jayp33.sonnenregen.data.CityStatus
import de.jayp33.sonnenregen.notification.NotificationViewBuilder
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationViewTest {
    @Test
    fun one_city_uses_the_city_as_header() {
        val view = build(listOf(status("berlin", "Sun in 2 h, no rain")))

        assertEquals("Berlin", view.title)
        assertEquals("Sun in 2 h, no rain", view.text)
        assertEquals(false, view.useInbox)
        assertEquals(emptyList<String>(), view.lines)
    }

    @Test
    fun one_stale_city_shows_unavailable_as_its_body() {
        val view = build(listOf(status("berlin", "Sun in 2 h, no rain", stale = true)))

        assertEquals("Berlin", view.title)
        assertEquals("Weather unavailable", view.text)
        assertEquals(false, view.useInbox)
    }

    @Test
    fun two_cities_use_an_inbox_with_the_app_name_as_header() {
        val view = build(
            listOf(
                status("berlin", "Sun in 2 h, no rain"),
                status("hamburg", "Rain in 30 min, sun in 5 h"),
            ),
        )

        assertEquals("Sonnenregen", view.title)
        assertEquals("Berlin: Sun in 2 h, no rain", view.text)
        assertEquals(true, view.useInbox)
        assertEquals(
            listOf(
                "Berlin: Sun in 2 h, no rain",
                "Hamburg: Rain in 30 min, sun in 5 h",
            ),
            view.lines,
        )
    }

    @Test
    fun no_cities_shows_the_empty_state() {
        val view = build(emptyList())

        assertEquals("Sonnenregen", view.title)
        assertEquals("No cities configured", view.text)
        assertEquals(true, view.useInbox)
        assertEquals(listOf("No cities configured"), view.lines)
    }

    private fun build(statuses: List<CityStatus>) = NotificationViewBuilder.build(
        statuses = statuses,
        appName = "Sonnenregen",
        noCitiesText = "No cities configured",
        textFor = { if (it.stale) "Weather unavailable" else it.compactSummary },
        lineFor = { city, text -> "$city: $text" },
    )

    private fun status(city: String, compactSummary: String, stale: Boolean = false) = CityStatus(
        city = city,
        compactSummary = compactSummary,
        fetchedAtMillis = 20L,
        stale = stale,
    )
}
