package de.jayp33.sonnenregen.data

import de.jayp33.sonnenregen.domain.displayCity

/** One city's line in the notification, in configured city order. */
data class CityStatus(
    val city: String,
    val displayName: String,
    val compactSummary: String,
    val fetchedAtMillis: Long,
    val stale: Boolean,
)

fun buildStatuses(
    cities: List<String>,
    forecasts: Map<String, CityForecast>,
    staleCities: Set<String> = emptySet(),
    hiddenCities: Set<String> = emptySet(),
): List<CityStatus> = cities.filterNot { it in hiddenCities }.mapNotNull { city ->
    val forecast = forecasts[city]
    when {
        forecast != null -> CityStatus(
            city = city,
            displayName = forecast.displayName.ifBlank { displayCity(city) },
            compactSummary = forecast.compactSummary,
            fetchedAtMillis = forecast.fetchedAtMillis,
            stale = city in staleCities,
        )
        // A city whose very first fetch failed still gets an "unavailable" line.
        city in staleCities -> CityStatus(
            city = city,
            displayName = displayCity(city),
            compactSummary = "",
            fetchedAtMillis = 0L,
            stale = true,
        )
        else -> null
    }
}
