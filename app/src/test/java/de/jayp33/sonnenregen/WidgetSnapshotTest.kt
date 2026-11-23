package de.jayp33.sonnenregen

import de.jayp33.sonnenregen.data.CityForecast
import de.jayp33.sonnenregen.domain.HourCell
import de.jayp33.sonnenregen.domain.HourKind
import de.jayp33.sonnenregen.widget.WidgetDay
import de.jayp33.sonnenregen.widget.widgetSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant

class WidgetSnapshotTest {
    private val base = Instant.parse("2026-11-23T12:00:00Z").toEpochMilli()
    private val hour = 3_600_000L

    private fun cell(offset: Long, kind: HourKind = HourKind.SUN) = HourCell(
        startEpochMillis = base + offset,
        kind = kind,
        zoneId = "UTC",
    )

    private fun forecast(
        city: String = "berlin",
        hours: List<HourCell> = (0 until 48).map { cell(it * hour) },
        nowcast: List<HourCell> = emptyList(),
    ) = CityForecast(
        city = city,
        compactSummary = "Sun in 2 h, no rain",
        sunSummary = "sun",
        rainSummary = "rain",
        fetchedAtMillis = base,
        hours = hours,
        nowcastCells = nowcast,
        displayName = "Berlin",
    )

    @Test
    fun defaults_to_first_visible_city() {
        val snapshot = widgetSnapshot(
            cities = listOf("berlin", "gleina"),
            forecasts = mapOf("berlin" to forecast(), "gleina" to forecast("gleina")),
            hiddenCities = emptySet(),
            widgetCity = null,
            nowMillis = base,
        )

        assertEquals("berlin", snapshot.city)
        assertEquals("Berlin", snapshot.displayName)
        assertEquals("Sun in 2 h, no rain", snapshot.compactSummary)
    }

    @Test
    fun explicit_choice_wins_but_hidden_or_unknown_choice_falls_back() {
        val cities = listOf("berlin", "gleina")
        val forecasts = mapOf("berlin" to forecast(), "gleina" to forecast("gleina"))

        val chosen = widgetSnapshot(cities, forecasts, emptySet(), "gleina", base)
        val hidden = widgetSnapshot(cities, forecasts, setOf("gleina"), "gleina", base)
        val unknown = widgetSnapshot(cities, forecasts, emptySet(), "paris", base)

        assertEquals("gleina", chosen.city)
        assertEquals("berlin", hidden.city)
        assertEquals("berlin", unknown.city)
    }

    @Test
    fun no_visible_cities_gives_empty_snapshot() {
        val snapshot = widgetSnapshot(
            listOf("berlin"), mapOf("berlin" to forecast()), setOf("berlin"), null, base,
        )

        assertNull(snapshot.city)
        assertTrue(snapshot.days.isEmpty())
    }

    @Test
    fun drops_elapsed_hours_and_pads_the_two_rows() {
        val snapshot = widgetSnapshot(
            listOf("berlin"),
            mapOf("berlin" to forecast()),
            emptySet(),
            null,
            base + 2 * hour + 34 * 60_000L,
        )

        assertEquals(2, snapshot.days.size)
        assertEquals(base + 2 * hour, snapshot.days[0].cells.first()?.startEpochMillis)
        assertTrue(snapshot.days.all { it.cells.size == 24 })
        assertEquals(listOf(null, null), snapshot.days[1].cells.takeLast(2))
        assertEquals(DayOfWeek.MONDAY, snapshot.days[0].day)
        assertFalse(snapshot.hoursStale)
    }

    @Test
    fun elapsed_only_hours_are_flagged_as_stale() {
        val snapshot = widgetSnapshot(
            listOf("berlin"),
            mapOf("berlin" to forecast(hours = listOf(cell(-2 * hour), cell(-hour)))),
            emptySet(),
            null,
            base,
        )

        assertTrue(snapshot.days.isEmpty())
        assertTrue(snapshot.hoursStale)
    }

    @Test
    fun nowcast_keeps_current_cells_only_when_weather_is_expected() {
        val nowcast = listOf(
            cell(-15 * 60_000L, HourKind.RAIN),
            cell(0L, HourKind.SUN),
            cell(15 * 60_000L, HourKind.CLOUDY),
        )
        val snapshot = widgetSnapshot(
            listOf("berlin"),
            mapOf("berlin" to forecast(nowcast = nowcast)),
            emptySet(),
            null,
            base,
        )

        assertEquals(listOf(base, base + 15 * 60_000L), snapshot.nowcastCells.map { it.startEpochMillis })
        assertFalse(snapshot.nowcastStale)
    }

    @Test
    fun nowcast_without_weather_is_hidden_but_not_stale() {
        val snapshot = widgetSnapshot(
            listOf("berlin"),
            mapOf("berlin" to forecast(nowcast = listOf(cell(0L, HourKind.CLOUDY)))),
            emptySet(),
            null,
            base,
        )

        assertTrue(snapshot.nowcastCells.isEmpty())
        assertFalse(snapshot.nowcastStale)
    }

    @Test
    fun elapsed_only_nowcast_is_flagged_as_stale() {
        val snapshot = widgetSnapshot(
            listOf("berlin"),
            mapOf("berlin" to forecast(nowcast = listOf(cell(-hour, HourKind.RAIN)))),
            emptySet(),
            null,
            base,
        )

        assertTrue(snapshot.nowcastCells.isEmpty())
        assertTrue(snapshot.nowcastStale)
    }

    @Test
    fun missing_forecast_still_selects_city_with_empty_summary() {
        val snapshot = widgetSnapshot(listOf("berlin"), emptyMap(), emptySet(), null, base)

        assertEquals("berlin", snapshot.city)
        assertEquals("Berlin", snapshot.displayName)
        assertEquals("", snapshot.compactSummary)
        assertEquals(emptyList<WidgetDay>(), snapshot.days)
    }
}
