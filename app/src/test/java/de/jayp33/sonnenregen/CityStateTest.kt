package de.jayp33.sonnenregen

import de.jayp33.sonnenregen.data.CityForecast
import de.jayp33.sonnenregen.data.buildStatuses
import de.jayp33.sonnenregen.data.mergeForecasts
import de.jayp33.sonnenregen.data.reorderCity
import de.jayp33.sonnenregen.data.updateNotificationVisibility
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
    fun reordering_moves_one_city_and_keeps_the_others() {
        val order = listOf("berlin", "hamburg", "munich")

        assertEquals(
            listOf("hamburg", "berlin", "munich"),
            reorderCity(order, "berlin", 1),
        )
        assertEquals(
            listOf("berlin", "munich", "hamburg"),
            reorderCity(order, "hamburg", 1),
        )
        assertEquals(order, reorderCity(order, "berlin", -1))
        assertEquals(order, reorderCity(order, "unknown", 1))
    }

    @Test
    fun hidden_cities_are_left_out_of_the_notification() {
        val statuses = buildStatuses(
            cities = listOf("berlin", "hamburg", "munich"),
            forecasts = mapOf(
                "berlin" to forecast("berlin", fetchedAtMillis = 20L),
                "hamburg" to forecast("hamburg", fetchedAtMillis = 10L),
                "munich" to forecast("munich", fetchedAtMillis = 5L),
            ),
            hiddenCities = setOf("hamburg"),
        )

        assertEquals(listOf("berlin", "munich"), statuses.map { it.city })
    }

    @Test
    fun notification_visibility_toggles_one_city_at_a_time() {
        val hidden = updateNotificationVisibility(emptySet(), "hamburg", visible = false)

        assertEquals(setOf("hamburg"), hidden)
        assertEquals(emptySet<String>(), updateNotificationVisibility(hidden, "hamburg", true))
        assertEquals(
            setOf("hamburg", "berlin"),
            updateNotificationVisibility(hidden, "berlin", false),
        )
    }

    @Test
    fun statuses_prefer_the_real_display_name() {
        val berlin = forecast("berlin", fetchedAtMillis = 20L)
            .copy(displayName = "Frankfurt (Oder)")

        val statuses = buildStatuses(
            cities = listOf("berlin"),
            forecasts = mapOf("berlin" to berlin),
        )

        assertEquals("Frankfurt (Oder)", statuses.single().displayName)
    }

    @Test
    fun statuses_fall_back_to_the_slug_when_no_name_was_fetched_yet() {
        val statuses = buildStatuses(
            cities = listOf("berlin"),
            forecasts = mapOf("berlin" to forecast("berlin", fetchedAtMillis = 20L)),
        )

        assertEquals("Berlin", statuses.single().displayName)
    }

    @Test
    fun hourly_cells_survive_json_round_trip() {
        val original = forecast("berlin", 20L).copy(
            displayName = "Frankfurt (Oder)",
            hours = listOf(HourCell(1_000L, HourKind.HEAVY_RAIN, "-05:00")),
            nowcastCells = listOf(HourCell(2_000L, HourKind.RAIN, "+01:00")),
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

        val decoded = Json.decodeFromString<CityForecast>(legacy)

        assertEquals(emptyList<HourCell>(), decoded.hours)
        assertEquals(emptyList<HourCell>(), decoded.nowcastCells)
        assertEquals("", decoded.displayName)
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
