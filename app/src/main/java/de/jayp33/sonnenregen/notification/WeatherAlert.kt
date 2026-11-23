package de.jayp33.sonnenregen.notification

import de.jayp33.sonnenregen.data.CityForecast
import de.jayp33.sonnenregen.domain.displayCity

/** Which condition a per-city alert is about. */
enum class AlertKind { SUN, RAIN }

/** One per-city, per-condition onset alert to post. */
data class WeatherAlert(
    val city: String,
    val displayName: String,
    val kind: AlertKind,
)

/**
 * Alerts only on a fresh forecast's transition from "not happening" to
 * "happening". A city without a previous forecast is skipped so enabling an
 * alert never fires for a condition that was already ongoing.
 */
object AlertBuilder {
    fun onsetAlerts(
        previous: Map<String, CityForecast>,
        updates: List<CityForecast>,
        sunAlertCities: Set<String>,
        rainAlertCities: Set<String>,
    ): List<WeatherAlert> = updates.flatMap { next ->
        val prior = previous[next.city] ?: return@flatMap emptyList()
        val displayName = next.displayName.ifBlank { displayCity(next.city) }
        buildList {
            if (next.city in sunAlertCities && !prior.sunCurrently && next.sunCurrently) {
                add(WeatherAlert(next.city, displayName, AlertKind.SUN))
            }
            if (next.city in rainAlertCities && !prior.rainCurrently && next.rainCurrently) {
                add(WeatherAlert(next.city, displayName, AlertKind.RAIN))
            }
        }
    }
}
