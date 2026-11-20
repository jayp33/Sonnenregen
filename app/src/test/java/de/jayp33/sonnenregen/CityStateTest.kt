package de.jayp33.sonnenregen

import de.jayp33.sonnenregen.data.CityForecast
import de.jayp33.sonnenregen.data.buildStatuses
import de.jayp33.sonnenregen.data.mergeForecasts
import org.junit.Assert.assertEquals
import org.junit.Test

class CityStateTest {
    @Test
    fun successful_cities_replace_their_previous_forecast() {
        val berlin = forecast("berlin", fetchedAtMillis = 20L)
        val hamburg = forecast("hamburg", fetchedAtMillis = 10L)

        val merged = mergeForecasts(
            previous = mapOf("berlin" to forecast("berlin", fetchedAtMillis = 1L), "hamburg" to hamburg),
            updates = listOf(berlin),
        )

        assertEquals(mapOf("berlin" to berlin, "hamburg" to hamburg), merged)
    }

    @Test
    fun statuses_follow_configured_order_and_skip_missing_forecasts() {
        val berlin = forecast("berlin", fetchedAtMillis = 20L)
        val hamburg = forecast("hamburg", fetchedAtMillis = 10L)

        val statuses = buildStatuses(
            cities = listOf("hamburg", "munich", "berlin"),
            forecasts = mapOf("berlin" to berlin, "hamburg" to hamburg),
            staleCities = setOf("hamburg"),
        )

        assertEquals(2, statuses.size)
        assertEquals("hamburg", statuses[0].city)
        assertEquals(true, statuses[0].stale)
        assertEquals(10L, statuses[0].fetchedAtMillis)
        assertEquals("berlin", statuses[1].city)
        assertEquals(false, statuses[1].stale)
        assertEquals("Sun in 2 h, no rain", statuses[1].compactSummary)
    }

    @Test
    fun a_city_that_never_fetched_is_still_shown_as_stale() {
        val statuses = buildStatuses(
            cities = listOf("berlin", "hamburg"),
            forecasts = mapOf("berlin" to forecast("berlin", fetchedAtMillis = 20L)),
            staleCities = setOf("hamburg"),
        )

        assertEquals(2, statuses.size)
        assertEquals("hamburg", statuses[1].city)
        assertEquals(true, statuses[1].stale)
        assertEquals(0L, statuses[1].fetchedAtMillis)
    }

    private fun forecast(city: String, fetchedAtMillis: Long) = CityForecast(
        city = city,
        compactSummary = "Sun in 2 h, no rain",
        sunSummary = "Sunshine starts in 2 h.",
        rainSummary = "No rain expected.",
        fetchedAtMillis = fetchedAtMillis,
    )
}
