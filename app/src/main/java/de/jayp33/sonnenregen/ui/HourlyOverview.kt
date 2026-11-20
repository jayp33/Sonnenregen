package de.jayp33.sonnenregen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.jayp33.sonnenregen.R
import de.jayp33.sonnenregen.domain.HourCell
import de.jayp33.sonnenregen.domain.HourKind
import de.jayp33.sonnenregen.domain.groupHoursByDay
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * One row per calendar day in the forecast's own timezone. Rows are padded to
 * 24 columns so hours line up vertically without scrolling.
 */
@Composable
fun HourlyOverview(city: String, hours: List<HourCell>, modifier: Modifier = Modifier) {
    if (hours.isEmpty()) return

    val locale = LocalContext.current.resources.configuration.locales.get(0) ?: Locale.getDefault()
    val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale)
    val byDay = groupHoursByDay(hours)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        byDay.forEach { (date, byHour) ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = date.format(dateFormat),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.width(64.dp),
                )
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    (0..23).forEach { hour ->
                        val cell = byHour[hour]
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
                                    .semantics(mergeDescendants = true) {
                                        contentDescription = description
                                    }
                            },
                        )
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

private fun HourKind.color(): Color = when (this) {
    HourKind.SUN -> Color(0xFFF2C230)
    HourKind.PARTLY_SUN -> Color(0xFFF7DE8C)
    HourKind.CLOUDY -> Color(0xFFBFC4C9)
    HourKind.OVERCAST -> Color(0xFF7C848C)
    HourKind.CLEAR_NIGHT -> Color(0xFF2F3A4A)
    HourKind.CLOUDY_NIGHT -> Color(0xFF4A5561)
    HourKind.LIGHT_RAIN -> Color(0xFFA9D2F2)
    HourKind.RAIN -> Color(0xFF4C9BD6)
    HourKind.HEAVY_RAIN -> Color(0xFF1F5C99)
}

private fun HourKind.label(): Int = when (this) {
    HourKind.SUN -> R.string.hour_sun
    HourKind.PARTLY_SUN -> R.string.hour_partly_sun
    HourKind.CLOUDY -> R.string.hour_cloudy
    HourKind.OVERCAST -> R.string.hour_overcast
    HourKind.CLEAR_NIGHT -> R.string.hour_clear_night
    HourKind.CLOUDY_NIGHT -> R.string.hour_cloudy_night
    HourKind.LIGHT_RAIN -> R.string.hour_light_rain
    HourKind.RAIN -> R.string.hour_rain
    HourKind.HEAVY_RAIN -> R.string.hour_heavy_rain
}
