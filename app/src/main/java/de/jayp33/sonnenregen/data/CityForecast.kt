package de.jayp33.sonnenregen.data

import kotlinx.serialization.Serializable

/** The last good forecast for one city, already formatted in the active locale. */
@Serializable
data class CityForecast(
    val city: String,
    val compactSummary: String,
    val sunSummary: String,
    val rainSummary: String,
    val fetchedAtMillis: Long,
    val sunriseLabel: String? = null,
    val sunsetLabel: String? = null,
)

/** Keep successful cities and retain the previous value for failed ones. */
fun mergeForecasts(
    previous: Map<String, CityForecast>,
    updates: List<CityForecast>,
): Map<String, CityForecast> = previous + updates.associateBy { it.city }
