package de.jayp33.sonnenregen

import de.jayp33.sonnenregen.data.CityForecast
import de.jayp33.sonnenregen.data.buildStatuses
import de.jayp33.sonnenregen.data.mergeForecasts
import de.jayp33.sonnenregen.domain.HourCell
import de.jayp33.sonnenregen.domain.HourKind
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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

    @Test
    fun hourly_cells_survive_json_round_trip() {
        val original = forecast("berlin", 20L).copy(
            hours = listOf(HourCell(1_000L, HourKind.HEAVY_RAIN, "-05:00")),
        )

        val encoded = Json.encodeToString(original)

        assertEquals(original, Json.decodeFromString<CityForecast>(encoded))
    }

    @Test
    fun legacy_forecast_without_hours_defaults_to_empty() {
        val legacy = """
            {"city":"berlin","compactSummary":"Sun in 2 h, no rain",
             "sunSummary":"Sunshine starts in 2 h.","rainSummary":"No rain expected.",
             "fetchedAtMillis":20}
        """.trimIndent()

        assertEquals(emptyList<HourCell>(), Json.decodeFromString<CityForecast>(legacy).hours)
    }

    @Test
    fun legacy_hour_cell_defaults_to_berlin_zone() {
        val legacy = """{"startEpochMillis":1000,"kind":"SUN"}"""

        assertEquals("Europe/Berlin", Json.decodeFromString<HourCell>(legacy).zoneId)
    }

    private fun forecast(city: String, fetchedAtMillis: Long) = CityForecast(
        city = city,
        compactSummary = "Sun in 2 h, no rain",
        sunSummary = "Sunshine starts in 2 h.",
        rainSummary = "No rain expected.",
        fetchedAtMillis = fetchedAtMillis,
    )
}
