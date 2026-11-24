package de.jayp33.sonnenregen.data

import de.jayp33.sonnenregen.domain.displayCity
import de.jayp33.sonnenregen.domain.displayNameFor

/** After this age the data is explicitly marked outdated, not just aged. */
const val STALE_AGE_MILLIS = 30L * 60 * 1000

/** One city's line in the notification, in configured city order. */
data class CityStatus(
    val city: String,
    val displayName: String,
    val compactSummary: String,
    val fetchedAtMillis: Long,
    val stale: Boolean,
    val outdated: Boolean = false,
)

fun buildStatuses(
    cities: List<String>,
    forecasts: Map<String, CityForecast>,
    staleCities: Set<String> = emptySet(),
    hiddenCities: Set<String> = emptySet(),
    nowMillis: Long = 0L,
    compactFor: (CityForecast) -> String = { it.compactSummary },
): List<CityStatus> = cities.filterNot { it in hiddenCities }.mapNotNull { city ->
    val forecast = forecasts[city]
    when {
        forecast != null -> CityStatus(
            city = city,
            displayName = displayNameFor(city, forecast.displayName),
            compactSummary = compactFor(forecast),
            fetchedAtMillis = forecast.fetchedAtMillis,
            stale = city in staleCities,
            outdated = nowMillis > 0L &&
                (nowMillis - forecast.fetchedAtMillis) >= STALE_AGE_MILLIS,
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
