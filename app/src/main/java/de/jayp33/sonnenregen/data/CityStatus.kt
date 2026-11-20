package de.jayp33.sonnenregen.data

/** One city's line in the notification, in configured city order. */
data class CityStatus(
    val city: String,
    val compactSummary: String,
    val fetchedAtMillis: Long,
    val stale: Boolean,
)

fun buildStatuses(
    cities: List<String>,
    forecasts: Map<String, CityForecast>,
    staleCities: Set<String> = emptySet(),
): List<CityStatus> = cities.mapNotNull { city ->
    val forecast = forecasts[city]
    when {
        forecast != null -> CityStatus(
            city = city,
            compactSummary = forecast.compactSummary,
            fetchedAtMillis = forecast.fetchedAtMillis,
            stale = city in staleCities,
        )
        // A city whose very first fetch failed still gets an "unavailable" line.
        city in staleCities -> CityStatus(city, "", 0L, stale = true)
        else -> null
    }
}
