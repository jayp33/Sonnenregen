package de.jayp33.sonnenregen.widget

import de.jayp33.sonnenregen.data.CityForecast
import de.jayp33.sonnenregen.domain.HourCell
import de.jayp33.sonnenregen.domain.currentOrFutureHours
import de.jayp33.sonnenregen.domain.currentOrFutureNowcast
import de.jayp33.sonnenregen.domain.displayNameFor
import de.jayp33.sonnenregen.domain.isRain
import de.jayp33.sonnenregen.domain.isSnow
import de.jayp33.sonnenregen.domain.isSun
import de.jayp33.sonnenregen.domain.zone
import java.time.DayOfWeek
import java.time.Instant

/** One 24-hour row, labelled with the weekday of its first cell. */
data class WidgetDay(val day: DayOfWeek, val cells: List<HourCell?>)

/** Everything the widget needs, derived from stored forecasts without I/O. */
data class WidgetSnapshot(
    val city: String?,
    val displayName: String,
    val compactSummary: String,
    val fetchedAtMillis: Long,
    val days: List<WidgetDay>,
    val nowcastCells: List<HourCell>,
    val hoursStale: Boolean,
    val nowcastStale: Boolean,
)

/**
 * Select the widget city (explicit choice, else first visible) and turn its
 * forecast into two 24-hour rows plus the current 90-minute window. Past cells
 * are dropped; elapsed-only data is flagged so the widget can say so instead of
 * showing an empty grid.
 */
fun widgetSnapshot(
    cities: List<String>,
    forecasts: Map<String, CityForecast>,
    hiddenCities: Set<String>,
    widgetCity: String?,
    nowMillis: Long,
): WidgetSnapshot {
    val visible = cities.filterNot { it in hiddenCities }
    val selected = widgetCity?.takeIf { it in visible } ?: visible.firstOrNull()
        ?: return WidgetSnapshot(null, "", "", 0L, emptyList(), emptyList(), false, false)

    val forecast = forecasts[selected]
        ?: return WidgetSnapshot(
            selected,
            displayNameFor(selected, null),
            "",
            0L,
            emptyList(),
            emptyList(),
            false,
            false,
        )

    val hours = forecast.hours.currentOrFutureHours(nowMillis)
    val padded = hours + List((24 - hours.size % 24) % 24) { null }
    val days = padded.chunked(24).map { row ->
        WidgetDay(
            day = row.firstNotNullOfOrNull { it }?.dayOfWeek() ?: DayOfWeek.MONDAY,
            cells = row,
        )
    }

    val storedNowcast = forecast.nowcastCells
    val futureNowcast = storedNowcast.currentOrFutureNowcast(nowMillis)
    val nowcast = if (futureNowcast.any { it.hasWeather() }) futureNowcast else emptyList()

    return WidgetSnapshot(
        city = selected,
        displayName = displayNameFor(selected, forecast.displayName),
        compactSummary = forecast.compactSummary,
        fetchedAtMillis = forecast.fetchedAtMillis,
        days = days,
        nowcastCells = nowcast,
        hoursStale = forecast.hours.isNotEmpty() && hours.isEmpty(),
        nowcastStale = storedNowcast.isNotEmpty() && futureNowcast.isEmpty(),
    )
}

private fun HourCell.dayOfWeek(): DayOfWeek =
    Instant.ofEpochMilli(startEpochMillis).atZone(zone()).dayOfWeek

private fun HourCell.hasWeather(): Boolean = kind.isSun() || kind.isRain() || kind.isSnow()
