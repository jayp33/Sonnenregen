package de.jayp33.sonnenregen

import de.jayp33.sonnenregen.data.CityForecast
import de.jayp33.sonnenregen.notification.AlertBuilder
import de.jayp33.sonnenregen.notification.AlertKind
import org.junit.Assert.assertEquals
import org.junit.Test

class AlertBuilderTest {
    @Test
    fun sun_onset_alert_uses_the_fresh_display_name() {
        val previous = mapOf("berlin" to forecast("berlin"))
        val update = forecast("berlin", sun = true).copy(displayName = "Berlin Mitte")

        val alerts = AlertBuilder.onsetAlerts(
            previous = previous,
            updates = listOf(update),
            sunAlertCities = setOf("berlin"),
            rainAlertCities = emptySet(),
        )

        assertEquals(1, alerts.size)
        assertEquals("berlin", alerts.single().city)
        assertEquals("Berlin Mitte", alerts.single().displayName)
        assertEquals(AlertKind.SUN, alerts.single().kind)
    }

    @Test
    fun rain_onset_alert_uses_the_slug_when_no_name_was_fetched() {
        val alerts = AlertBuilder.onsetAlerts(
            previous = mapOf("berlin" to forecast("berlin")),
            updates = listOf(forecast("berlin", rain = true)),
            sunAlertCities = emptySet(),
            rainAlertCities = setOf("berlin"),
        )

        assertEquals(1, alerts.size)
        assertEquals("Berlin", alerts.single().displayName)
        assertEquals(AlertKind.RAIN, alerts.single().kind)
    }

    @Test
    fun disabled_conditions_do_not_alert() {
        val alerts = AlertBuilder.onsetAlerts(
            previous = mapOf("berlin" to forecast("berlin")),
            updates = listOf(forecast("berlin", sun = true, rain = true)),
            sunAlertCities = emptySet(),
            rainAlertCities = emptySet(),
        )

        assertEquals(emptyList<Any>(), alerts)
    }

    @Test
    fun a_city_without_a_previous_forecast_does_not_alert() {
        val alerts = AlertBuilder.onsetAlerts(
            previous = emptyMap(),
            updates = listOf(forecast("berlin", sun = true, rain = true)),
            sunAlertCities = setOf("berlin"),
            rainAlertCities = setOf("berlin"),
        )

        assertEquals(emptyList<Any>(), alerts)
    }

    @Test
    fun an_ongoing_condition_does_not_repeat() {
        val alerts = AlertBuilder.onsetAlerts(
            previous = mapOf("berlin" to forecast("berlin", sun = true, rain = true)),
            updates = listOf(forecast("berlin", sun = true, rain = true)),
            sunAlertCities = setOf("berlin"),
            rainAlertCities = setOf("berlin"),
        )

        assertEquals(emptyList<Any>(), alerts)
    }

    @Test
    fun both_conditions_can_alert_in_one_refresh() {
        val alerts = AlertBuilder.onsetAlerts(
            previous = mapOf("berlin" to forecast("berlin")),
            updates = listOf(forecast("berlin", sun = true, rain = true)),
            sunAlertCities = setOf("berlin"),
            rainAlertCities = setOf("berlin"),
        )

        assertEquals(listOf(AlertKind.SUN, AlertKind.RAIN), alerts.map { it.kind })
    }

    private fun forecast(
        city: String,
        sun: Boolean = false,
        rain: Boolean = false,
    ) = CityForecast(
        city = city,
        compactSummary = "",
        sunSummary = "",
        rainSummary = "",
        fetchedAtMillis = 20L,
        sunCurrently = sun,
        rainCurrently = rain,
    )
}
