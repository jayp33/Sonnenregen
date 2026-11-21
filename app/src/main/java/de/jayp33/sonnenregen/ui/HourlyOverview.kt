package de.jayp33.sonnenregen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.jayp33.sonnenregen.R
import de.jayp33.sonnenregen.domain.HourCell
import de.jayp33.sonnenregen.domain.HourKind
import de.jayp33.sonnenregen.domain.groupHoursByDay

/**
 * One row per calendar day in the forecast's own timezone. Each day is split
 * into four blocks of six, separated by a small gap for orientation.
 */
@Composable
fun HourlyOverview(city: String, hours: List<HourCell>, modifier: Modifier = Modifier) {
    if (hours.isEmpty()) return

    val byDay = groupHoursByDay(hours)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        byDay.forEach { (_, byHour) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                (0..3).forEach { group ->
                    val firstHour = group * 6
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        (firstHour until firstHour + 6).forEach { hour ->
                            Cell(byHour[hour], hour, city)
                        }
                    }
                }
            }
        }
        Text(
            text = stringResource(R.string.hourly_legend),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun RowScope.Cell(cell: HourCell?, hour: Int, city: String) {
    val description = cell?.let {
        stringResource(
            R.string.hour_description,
            city,
            hour.toString().padStart(2, '0') + ":00",
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

@Suppress("DEPRECATION")
private fun HourKind.color(): Color = when (this) {
    HourKind.SUN, HourKind.CLEAR_NIGHT -> Color(0xFFF2C230)
    HourKind.PARTLY_SUN -> Color(0xFFF7DE8C)
    HourKind.CLOUDY, HourKind.OVERCAST, HourKind.CLOUDY_NIGHT -> Color(0xFFBFC4C9)
    HourKind.LIGHT_RAIN -> Color(0xFFA9D2F2)
    HourKind.RAIN -> Color(0xFF4C9BD6)
    HourKind.HEAVY_RAIN -> Color(0xFF1F5C99)
}

@Suppress("DEPRECATION")
private fun HourKind.label(): Int = when (this) {
    HourKind.SUN, HourKind.CLEAR_NIGHT -> R.string.hour_sun
    HourKind.PARTLY_SUN -> R.string.hour_partly_sun
    HourKind.CLOUDY, HourKind.CLOUDY_NIGHT -> R.string.hour_cloudy
    HourKind.OVERCAST -> R.string.hour_overcast
    HourKind.LIGHT_RAIN -> R.string.hour_light_rain
    HourKind.RAIN -> R.string.hour_rain
    HourKind.HEAVY_RAIN -> R.string.hour_heavy_rain
}
