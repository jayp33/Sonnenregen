package de.jayp33.sonnenregen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.jayp33.sonnenregen.R
import de.jayp33.sonnenregen.domain.CLOCK_TIME
import de.jayp33.sonnenregen.domain.HourCell
import de.jayp33.sonnenregen.domain.HourKind
import de.jayp33.sonnenregen.domain.SunEvent
import de.jayp33.sonnenregen.domain.SunEventKind
import de.jayp33.sonnenregen.domain.TIMEZONE
import de.jayp33.sonnenregen.domain.groupHoursByDay
import de.jayp33.sonnenregen.domain.zone
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

/** Vertical rhythm of the colour-grid rows. */
internal object WeatherRowSpacing {
    /** Day rows sit close together; the 90-minute row is clearly separated. */
    val betweenHourRows = 2.dp
    /** Extra space above the 90-minute row, on top of the city card's spacing. */
    val beforeNowcast = 6.dp
}

internal object WeatherRowTags {
    const val HOUR_ROW = "hour_row_"
    const val NOWCAST_ROW = "nowcast_row"
    const val STALE_HOURLY = "stale_hourly"
}

/**
 * One row per calendar day in the forecast's own timezone, prefixed with that
 * day's short weekday. Each day is split into four blocks of six, separated by
 * a small gap for orientation. Callers pass only current/future cells; missing
 * hours render as empty placeholders so the remaining hours keep their slots.
 */
@Composable
fun HourlyOverview(
    city: String,
    hours: List<HourCell>,
    sunEvents: List<SunEvent> = emptyList(),
    modifier: Modifier = Modifier,
) {
    if (hours.isEmpty()) return

    val byDay = groupHoursByDay(hours)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(WeatherRowSpacing.betweenHourRows)) {
        byDay.entries.forEachIndexed { index, (date, byHour) ->
            Row(
                modifier = Modifier.fillMaxWidth().testTag("${WeatherRowTags.HOUR_ROW}$index"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(date.dayOfWeek.weekdayLabel()),
                    modifier = Modifier.width(24.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                (0..3).forEach { group ->
                    val firstHour = group * 6
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        (firstHour until firstHour + 6).forEach { hour ->
                            val cell = byHour[hour]
                            Cell(
                                cell = cell,
                                time = hourLabel(hour),
                                city = city,
                                sunEvents = cell?.let { sunEventsFor(it, sunEvents) }.orEmpty(),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The finer 90-minute window, shown when it contains sun, rain or snow. Its six
 * quarter-hour cells use the same colours as the hourly grid and a short
 * `90` prefix in the same fixed-width column.
 */
@Composable
fun NowcastOverview(city: String, cells: List<HourCell>, modifier: Modifier = Modifier) {
    if (cells.isEmpty()) return

    Row(
        modifier = modifier.fillMaxWidth().testTag(WeatherRowTags.NOWCAST_ROW),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NowcastPrefix()
        cells.forEach { cell ->
            Cell(cell, cell.timeLabel(), city, showTime = true)
        }
    }
}

/**
 * Shown when every stored nowcast cell has elapsed. Keeping the prefixed row
 * visible with an explicit hint avoids reading a missing row as "no sun/rain".
 */
@Composable
internal fun StaleNowcastRow(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().testTag(WeatherRowTags.NOWCAST_ROW),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NowcastPrefix()
        Text(
            text = stringResource(R.string.nowcast_stale),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/**
 * Shown when every stored hourly cell has elapsed. An empty grid alone could
 * be read as "no weather", so the staleness is stated explicitly.
 */
@Composable
internal fun StaleHourlyOverview(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.hourly_stale),
        modifier = modifier.testTag(WeatherRowTags.STALE_HOURLY),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
}

@Composable
private fun NowcastPrefix() {
    Text(
        text = stringResource(R.string.nowcast_prefix),
        modifier = Modifier.width(24.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
}

@Composable
private fun RowScope.Cell(
    cell: HourCell?,
    time: String,
    city: String,
    sunEvents: List<SunEvent> = emptyList(),
    showTime: Boolean = false,
) {
    val eventLabels = mutableListOf<String>()
    if (cell != null && sunEvents.isNotEmpty()) {
        val zone = cell.zone()
        for (event in sunEvents) {
            eventLabels.add(stringResource(event.descriptionLabel(), event.timeLabel(zone)))
        }
    }
    val eventDetails = eventLabels.joinToString(", ")
    val description = cell?.let { current ->
        val weather = stringResource(current.kind.label())
        if (sunEvents.isEmpty()) {
            stringResource(R.string.hour_description, city, time, weather)
        } else {
            stringResource(R.string.hour_description_with_sun_events, city, time, weather, eventDetails)
        }
    }
    val base = Modifier.weight(1f).height(if (showTime) 24.dp else 12.dp)
    Box(
        modifier = if (cell == null || description == null) {
            base
        } else {
            base
                .background(cell.kind.color(), RoundedCornerShape(2.dp))
                .semantics(mergeDescendants = true) { contentDescription = description }
        },
    ) {
        if (showTime && cell != null) {
            Text(
                text = time,
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.labelSmall,
                color = cell.kind.onColor(),
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        } else if (cell != null && sunEvents.isNotEmpty()) {
            Text(
                text = sunEvents.joinToString("") { it.marker() },
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = cell.kind.onColor(),
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

/** Sunrise/sunset belonging to the same one-hour cell as a forecast slot. */
private fun sunEventsFor(cell: HourCell, events: List<SunEvent>): List<SunEvent> {
    if (events.isEmpty()) return emptyList()
    val zone = cell.zone()
    val start = Instant.ofEpochMilli(cell.startEpochMillis).atZone(zone)
    val end = start.plusHours(1)
    return events.filter { event ->
        val time = Instant.ofEpochMilli(event.atMillis).atZone(zone)
        !time.isBefore(start) && time.isBefore(end)
    }
}

private fun SunEvent.marker(): String = when (kind) {
    SunEventKind.SUNRISE -> "↑"
    SunEventKind.SUNSET -> "↓"
}

private fun SunEvent.descriptionLabel(): Int = when (kind) {
    SunEventKind.SUNRISE -> R.string.sunrise
    SunEventKind.SUNSET -> R.string.sunset
}

private fun SunEvent.timeLabel(zone: ZoneId): String =
    Instant.ofEpochMilli(atMillis).atZone(zone).toLocalTime().format(CLOCK_TIME)

/** White on the darker rain blues, black on the pale sun/cloud shades. */
private fun HourKind.onColor(): Color {
    val background = color()
    val luminance = 0.299 * background.red + 0.587 * background.green + 0.114 * background.blue
    return if (luminance > 0.5) Color.Black else Color.White
}

private fun hourLabel(hour: Int): String = hour.toString().padStart(2, '0') + ":00"

private fun HourCell.timeLabel(): String {
    val zone = try {
        ZoneId.of(zoneId)
    } catch (_: Exception) {
        ZoneId.of(TIMEZONE)
    }
    return Instant.ofEpochMilli(startEpochMillis).atZone(zone).toLocalTime().format(CLOCK_TIME)
}

internal fun DayOfWeek.weekdayLabel(): Int = when (this) {
    DayOfWeek.MONDAY -> R.string.weekday_monday
    DayOfWeek.TUESDAY -> R.string.weekday_tuesday
    DayOfWeek.WEDNESDAY -> R.string.weekday_wednesday
    DayOfWeek.THURSDAY -> R.string.weekday_thursday
    DayOfWeek.FRIDAY -> R.string.weekday_friday
    DayOfWeek.SATURDAY -> R.string.weekday_saturday
    DayOfWeek.SUNDAY -> R.string.weekday_sunday
}

@Suppress("DEPRECATION")
private fun HourKind.color(): Color = when (this) {
    HourKind.SUN -> Color(0xFFF2C230)
    HourKind.PARTLY_SUN -> Color(0xFFF7DE8C)
    HourKind.CLOUDY,
    HourKind.OVERCAST,
    HourKind.CLOUDY_NIGHT,
    HourKind.CLEAR_NIGHT,
    -> Color(0xFFE0E0E0)
    HourKind.LIGHT_RAIN -> Color(0xFFA9D2F2)
    HourKind.RAIN -> Color(0xFF4C9BD6)
    HourKind.HEAVY_RAIN -> Color(0xFF1F5C99)
    // Pink scale from WetterOnline's radar precipitation legend (light → heavy).
    HourKind.LIGHT_SNOW -> Color(0xFFFFC2FF)
    HourKind.SNOW -> Color(0xFFF969BF)
    HourKind.HEAVY_SNOW -> Color(0xFFEA39A1)
}

@Suppress("DEPRECATION")
private fun HourKind.label(): Int = when (this) {
    HourKind.SUN -> R.string.hour_sun
    HourKind.PARTLY_SUN -> R.string.hour_partly_sun
    HourKind.CLOUDY, HourKind.CLOUDY_NIGHT, HourKind.CLEAR_NIGHT -> R.string.hour_cloudy
    HourKind.OVERCAST -> R.string.hour_overcast
    HourKind.LIGHT_RAIN -> R.string.hour_light_rain
    HourKind.RAIN -> R.string.hour_rain
    HourKind.HEAVY_RAIN -> R.string.hour_heavy_rain
    HourKind.LIGHT_SNOW -> R.string.hour_light_snow
    HourKind.SNOW -> R.string.hour_snow
    HourKind.HEAVY_SNOW -> R.string.hour_heavy_snow
}
