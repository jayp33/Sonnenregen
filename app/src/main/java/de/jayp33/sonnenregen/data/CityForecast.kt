package de.jayp33.sonnenregen.data

import de.jayp33.sonnenregen.domain.HourCell
import de.jayp33.sonnenregen.domain.SunEvent
import de.jayp33.sonnenregen.domain.TIMEZONE
import kotlinx.serialization.Serializable

/** The last good forecast for one city, already formatted in the active locale. */
@Serializable
data class CityForecast(
    val city: String,
    val compactSummary: String,
    val sunSummary: String,
    val rainSummary: String,
    /** Snow line for the city details; blank when no snow is expected. */
    val snowSummary: String = "",
    val fetchedAtMillis: Long,
    val sunriseLabel: String? = null,
    val sunsetLabel: String? = null,
    /** Colour-grid cells for the 48-hour overview; empty in old installs. */
    val hours: List<HourCell> = emptyList(),
    /** Quarter-hour cells for the 90-minute row; empty in old installs. */
    val nowcastCells: List<HourCell> = emptyList(),
    /** Real place name from WetterOnline; blank in pre-name installs. */
    val displayName: String = "",
    /** Sunrise/sunset markers for the hourly grid; empty in old installs. */
    val sunEvents: List<SunEvent> = emptyList(),
    /** Structured onset state, used to detect sun/rain/snow alerts. Defaults for old installs. */
    val sunCurrently: Boolean = false,
    val rainCurrently: Boolean = false,
    val snowCurrently: Boolean = false,
    /** Upcoming onset time, so alerts can be scheduled ahead of the refresh. */
    val sunEventAtMillis: Long? = null,
    val rainEventAtMillis: Long? = null,
    val snowEventAtMillis: Long? = null,
    /** Zone of the forecast's clock times, used to render alert event times. */
    val timezone: String = TIMEZONE,
)

/** Keep successful cities and retain the previous value for failed ones. */
fun mergeForecasts(
    previous: Map<String, CityForecast>,
    updates: List<CityForecast>,
): Map<String, CityForecast> = previous + updates.associateBy { it.city }
