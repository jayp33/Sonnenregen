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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.jayp33.sonnenregen.R
import de.jayp33.sonnenregen.domain.CLOCK_TIME
import de.jayp33.sonnenregen.domain.HourCell
import de.jayp33.sonnenregen.domain.HourKind
import de.jayp33.sonnenregen.domain.TIMEZONE
import de.jayp33.sonnenregen.domain.groupHoursByDay
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

/**
 * One row per calendar day in the forecast's own timezone, prefixed with that
 * day's short weekday. Each day is split into four blocks of six, separated by
 * a small gap for orientation.
 */
@Composable
fun HourlyOverview(city: String, hours: List<HourCell>, modifier: Modifier = Modifier) {
    if (hours.isEmpty()) return

    val byDay = groupHoursByDay(hours)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        byDay.forEach { (date, byHour) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                            Cell(byHour[hour], hourLabel(hour), city)
                        }
                    }
                }
            }
        }
    }
}

/**
 * The finer 90-minute window, shown only when it contains rain. Its six
 * quarter-hour cells use the same colours as the hourly grid and a short
 * `90` prefix in the same fixed-width column.
 */
@Composable
fun NowcastOverview(city: String, cells: List<HourCell>, modifier: Modifier = Modifier) {
    if (cells.isEmpty()) return

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.nowcast_prefix),
            modifier = Modifier.width(24.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        cells.forEach { cell ->
            Cell(cell, cell.timeLabel(), city)
        }
    }
}

@Composable
private fun RowScope.Cell(cell: HourCell?, time: String, city: String) {
    val description = cell?.let {
        stringResource(
            R.string.hour_description,
            city,
            time,
            stringResource(it.kind.label()),
        )
    }
    val base = Modifier.weight(1f).height(12.dp)
    Box(
        modifier = if (cell == null || description == null) {
            base
        } else {
            base
                .background(cell.kind.color(), RoundedCornerShape(2.dp))
                .semantics(mergeDescendants = true) { contentDescription = description }
        },
    )
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
}
