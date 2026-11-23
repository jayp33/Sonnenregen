package de.jayp33.sonnenregen.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.LocalContext
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import de.jayp33.sonnenregen.R
import de.jayp33.sonnenregen.data.WeatherStore
import de.jayp33.sonnenregen.domain.CLOCK_TIME
import de.jayp33.sonnenregen.domain.HourCell
import de.jayp33.sonnenregen.domain.zone
import de.jayp33.sonnenregen.ui.MainActivity
import de.jayp33.sonnenregen.ui.color
import de.jayp33.sonnenregen.ui.label
import de.jayp33.sonnenregen.ui.weekdayLabel
import java.time.Instant
import java.time.ZoneId

class WeatherWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = WeatherStore(context)
        val snapshot = widgetSnapshot(
            cities = store.cities(),
            forecasts = store.forecasts(),
            hiddenCities = store.hiddenCities(),
            widgetCity = store.widgetCity(),
            nowMillis = System.currentTimeMillis(),
        )

        provideContent { WeatherWidgetContent(snapshot) }
    }
}

class WeatherWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeatherWidget()
}

/** Redraw every placed widget from the currently stored forecasts. */
suspend fun updateWeatherWidget(context: Context) = WeatherWidget().updateAll(context.applicationContext)

private val widgetBackground = ColorProvider(Color(0xFF1B1B1B))
private val primaryText = ColorProvider(Color.White)
private val secondaryText = ColorProvider(Color(0xFFBDBDBD))
private val titleStyle = TextStyle(color = primaryText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
private val summaryStyle = TextStyle(color = primaryText, fontSize = 11.sp)
private val smallStyle = TextStyle(color = secondaryText, fontSize = 10.sp)

/** Glance has no stringResource(); resolve strings through its LocalContext. */
@Composable
private fun string(id: Int, vararg args: Any): String = LocalContext.current.getString(id, *args)

@Composable
private fun WeatherWidgetContent(snapshot: WidgetSnapshot) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(widgetBackground)
            .padding(8.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        if (snapshot.city == null) {
            Text(
                text = string(R.string.no_cities_notification),
                style = summaryStyle,
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = snapshot.displayName, style = titleStyle)
                if (snapshot.fetchedAtMillis > 0L) {
                    Text(
                        text = string(R.string.widget_updated, formatTime(snapshot.fetchedAtMillis)),
                        style = smallStyle,
                    )
                }
            }
            Text(
                text = if (snapshot.compactSummary.isEmpty()) {
                    string(R.string.widget_no_forecast)
                } else {
                    snapshot.compactSummary
                },
                style = summaryStyle,
            )

            snapshot.days.forEach { day -> HourRow(day, snapshot.displayName) }
            if (snapshot.hoursStale) {
                Text(text = string(R.string.hourly_stale), style = smallStyle)
            }

            Row(
                modifier = GlanceModifier.padding(top = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = string(R.string.nowcast_prefix),
                    style = smallStyle,
                    modifier = GlanceModifier.width(20.dp),
                )
                // RemoteViews rows cap at 10 children, so nest the six cells.
                Column {
                    Row {
                        if (snapshot.nowcastStale) {
                            Text(text = string(R.string.nowcast_stale), style = smallStyle)
                        } else {
                            snapshot.nowcastCells.forEach { cell -> Cell(cell, snapshot.displayName) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HourRow(day: WidgetDay, city: String) {
    // RemoteViews rows cap at 10 children: one label + four nested groups.
    Row(
        modifier = GlanceModifier.padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = string(day.day.weekdayLabel()),
            style = smallStyle,
            modifier = GlanceModifier.width(20.dp),
        )
        (0..3).forEach { group ->
            Column(
                modifier = if (group == 0) {
                    GlanceModifier
                } else {
                    GlanceModifier.padding(start = 3.dp)
                },
            ) {
                Row {
                    (0..5).forEach { index ->
                        Cell(day.cells.getOrNull(group * 6 + index), city)
                    }
                }
            }
        }
    }
}

@Composable
private fun Cell(cell: HourCell?, city: String) {
    val description = cell?.let {
        val weather = string(it.kind.label())
        string(R.string.hour_description, city, it.timeLabel(), weather)
    }
    var modifier = GlanceModifier.size(7.dp)
    if (cell != null) modifier = modifier.background(ColorProvider(cell.kind.color()))
    if (description != null) modifier = modifier.semantics { contentDescription = description }
    Box(modifier = modifier) {}
}

private fun HourCell.timeLabel(): String =
    Instant.ofEpochMilli(startEpochMillis).atZone(zone()).format(CLOCK_TIME)

private fun formatTime(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(CLOCK_TIME)
