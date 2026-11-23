package de.jayp33.sonnenregen

import de.jayp33.sonnenregen.data.CityForecast
import de.jayp33.sonnenregen.data.decodeAlertStates
import de.jayp33.sonnenregen.data.decodeCities
import de.jayp33.sonnenregen.data.decodeCitySet
import de.jayp33.sonnenregen.data.decodeLeadMinutes
import de.jayp33.sonnenregen.data.decodeForecasts
import de.jayp33.sonnenregen.data.decodeHiddenCities
import de.jayp33.sonnenregen.notification.AlertStage
import de.jayp33.sonnenregen.notification.AlertState
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class CityPersistenceTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun invalid_stored_slugs_stay_visible_so_they_can_be_removed() {
        val stored = json.encodeToString(listOf("berlin", "bad slug", "BERLIN", ""))

        assertEquals(listOf("berlin", "bad slug"), decodeCities(stored, json))
    }

    @Test
    fun malformed_city_list_decodes_to_empty() {
        assertEquals(emptyList<String>(), decodeCities("not json", json))
    }

    @Test
    fun one_corrupt_forecast_does_not_hide_the_others() {
        val berlin = forecast("berlin")
        val hamburg = forecast("hamburg")
        val stored = """
            {
              "berlin": ${json.encodeToString(berlin)},
              "kaputt": {"city":"kaputt"},
              "hamburg": ${json.encodeToString(hamburg)}
            }
        """.trimIndent()

        val decoded = decodeForecasts(stored, json)

        assertEquals(setOf("berlin", "hamburg"), decoded.keys)
        assertEquals(berlin, decoded["berlin"])
        assertEquals(hamburg, decoded["hamburg"])
    }

    @Test
    fun forecast_keys_are_normalized_like_city_slugs() {
        val stored = """{" BERLIN ": ${json.encodeToString(forecast("berlin"))}}"""

        assertEquals(setOf("berlin"), decodeForecasts(stored, json).keys)
    }

    @Test
    fun alert_city_sets_reuse_the_hidden_city_decoder() {
        assertEquals(
            setOf("berlin", "hamburg"),
            decodeCitySet("""["BERLIN", "hamburg", ""]""", json),
        )
        assertEquals(emptySet<String>(), decodeCitySet("not json", json))
    }

    @Test
    fun malformed_forecast_map_decodes_to_empty() {
        assertEquals(emptyMap<String, CityForecast>(), decodeForecasts("[]", json))
        assertEquals(emptyMap<String, CityForecast>(), decodeForecasts("{", json))
    }

    @Test
    fun lead_minutes_are_normalized_and_restricted_to_offered_values() {
        val stored = """{"BERLIN":30,"hamburg":5,"":15}"""

        assertEquals(mapOf("berlin" to 30), decodeLeadMinutes(stored, json))
        assertEquals(emptyMap<String, Int>(), decodeLeadMinutes("not json", json))
        assertEquals(emptyMap<String, Int>(), decodeLeadMinutes(null, json))
    }

    @Test
    fun alert_states_survive_a_round_trip() {
        val states = mapOf(
            "berlin|SUN" to AlertState(AlertStage.SCHEDULED, 5_000L),
            "berlin|RAIN" to AlertState(AlertStage.NOTIFIED, 9_000L, episodeEnded = true),
            "berlin|SNOW" to AlertState(AlertStage.SCHEDULED, 13_000L),
        )

        assertEquals(states, decodeAlertStates(json.encodeToString(states), json))
        assertEquals(emptyMap<String, AlertState>(), decodeAlertStates("not json", json))
    }

    @Test
    fun snow_onset_fields_survive_a_round_trip() {
        val forecast = forecast("berlin").copy(snowEventAtMillis = 99_000L)
        val stored = json.encodeToString(mapOf("berlin" to forecast))

        assertEquals(forecast, decodeForecasts(stored, json)["berlin"])
    }

    @Test
    fun hidden_cities_keep_invalid_entries_and_normalize_them() {
        val stored = """["Berlin", "hamburg", "", "BERLIN"]"""

        assertEquals(setOf("berlin", "hamburg"), decodeHiddenCities(stored, json))
    }

    private fun forecast(city: String) = CityForecast(
        city = city,
        compactSummary = "Sun in 2 h, no rain",
        sunSummary = "Sunshine starts in 2 h.",
        rainSummary = "No rain expected.",
        fetchedAtMillis = 20L,
    )
}
