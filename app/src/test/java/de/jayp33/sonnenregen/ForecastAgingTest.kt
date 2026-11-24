package de.jayp33.sonnenregen

import de.jayp33.sonnenregen.data.CityForecast
import de.jayp33.sonnenregen.data.buildStatuses
import de.jayp33.sonnenregen.data.cityJson
import de.jayp33.sonnenregen.domain.Change
import de.jayp33.sonnenregen.domain.ChangeSnapshot
import de.jayp33.sonnenregen.domain.Condition
import de.jayp33.sonnenregen.domain.Event
import de.jayp33.sonnenregen.text.ForecastFormatter
import de.jayp33.sonnenregen.text.ForecastPhrases
import de.jayp33.sonnenregen.text.ForecastText
import de.jayp33.sonnenregen.work.staleCitiesAfterFetch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Test
import java.time.OffsetDateTime

class ForecastAgingTest {
    private val reference = OffsetDateTime.parse("2026-11-24T10:00:00+01:00")
    private val fetchedAt = reference.toInstant().toEpochMilli()

    @Test
    fun compact_line_ages_without_a_refresh() {
        val sun = Change(false, Event.STARTS, reference.plusHours(6), 360L, Condition.SUN)
        assertEquals("Sun in 6 h, no rain", ForecastFormatter.compact(sun, noRain, english))

        val later = reference.plusHours(1)
        assertEquals(
            "Sun in 5 h, no rain",
            ForecastFormatter.compact(sun, noRain, english, now = later),
        )
        assertEquals(
            "Sonne in 5 Std., kein Regen",
            ForecastFormatter.compact(sun, noRain, german, now = later),
        )
    }

    @Test
    fun passed_events_fall_back_to_their_terminal_state() {
        val starts = Change(false, Event.STARTS, reference.plusHours(1), 60L, Condition.SUN)
        val ends = Change(true, Event.ENDS, reference.plusHours(1), 60L, Condition.SUN)
        val later = reference.plusHours(2)

        assertEquals("Sun now, no rain", ForecastFormatter.compact(starts, noRain, english, now = later))
        assertEquals("no sun, no rain", ForecastFormatter.compact(ends, noRain, english, now = later))
        assertEquals(
            "Sunshine is not expected to end within the forecast horizon.",
            ForecastFormatter.verbose(starts, english, now = later),
        )
    }

    @Test
    fun verbose_line_ages_but_keeps_the_absolute_time() {
        val sun = Change(false, Event.STARTS, reference.plusHours(6), 360L, Condition.SUN)
        assertEquals(
            "Sunshine starts in 5 h (2026-11-24 16:00 +0100).",
            ForecastFormatter.verbose(sun, english, now = reference.plusHours(1)),
        )
    }

    @Test
    fun the_second_shower_hint_ages_with_its_absolute_time() {
        val sun = Change(false, Event.STARTS, reference.plusHours(5), 300L, Condition.SUN)
        val rain = Change(false, Event.STARTS, reference.plusMinutes(15), 15L, Condition.RAIN)
        assertEquals(
            "Rain in 5 min (again in 50 min), sun in 4 h 50 min",
            ForecastFormatter.compact(
                sun = sun,
                rain = rain,
                phrases = english,
                rainAgainInMinutes = 50L,
                now = reference.plusMinutes(10),
            ),
        )
    }

    @Test
    fun stored_changes_render_an_aged_compact_line() {
        val forecast = storedForecast().copy(
            sunChange = ChangeSnapshot(
                condition = Condition.SUN,
                currently = false,
                event = Event.STARTS,
                atEpochMillis = reference.plusHours(6).toInstant().toEpochMilli(),
            ),
            rainChange = ChangeSnapshot(condition = Condition.RAIN, currently = false),
        )
        val now = reference.plusHours(1).toInstant().toEpochMilli()
        assertEquals("Sun in 5 h, no rain", ForecastText.compact(forecast, now, english))
        assertEquals(
            "Sunshine starts in 5 h (2026-11-24 16:00 +0100).",
            ForecastText.sunSummary(forecast, now, english),
        )
    }

    @Test
    fun forecasts_saved_without_changes_keep_their_original_strings() {
        val forecast = storedForecast()
        val now = reference.plusHours(3).toInstant().toEpochMilli()
        assertEquals(forecast.compactSummary, ForecastText.compact(forecast, now, english))
        assertEquals(forecast.sunSummary, ForecastText.sunSummary(forecast, now, english))
        assertEquals(forecast.rainSummary, ForecastText.rainSummary(forecast, now, english))
    }

    @Test
    fun statuses_carry_the_aged_summary_and_an_outdated_hint() {
        val forecast = storedForecast().copy(
            sunChange = ChangeSnapshot(
                condition = Condition.SUN,
                currently = false,
                event = Event.STARTS,
                atEpochMillis = reference.plusHours(6).toInstant().toEpochMilli(),
            ),
            rainChange = ChangeSnapshot(condition = Condition.RAIN, currently = false),
        )
        val recent = buildStatuses(
            cities = listOf("berlin"),
            forecasts = mapOf("berlin" to forecast),
            nowMillis = reference.plusMinutes(20).toInstant().toEpochMilli(),
            compactFor = { ForecastText.compact(it, reference.plusMinutes(20).toInstant().toEpochMilli(), english) },
        ).single()
        assertFalse(recent.outdated)
        assertEquals("Sun in 5 h 40 min, no rain", recent.compactSummary)

        val outdated = buildStatuses(
            cities = listOf("berlin"),
            forecasts = mapOf("berlin" to forecast),
            nowMillis = reference.plusHours(1).toInstant().toEpochMilli(),
            compactFor = { ForecastText.compact(it, reference.plusHours(1).toInstant().toEpochMilli(), english) },
        ).single()
        assertTrue(outdated.outdated)
        assertEquals("Sun in 5 h, no rain", outdated.compactSummary)
    }

    @Test
    fun change_snapshots_survive_a_datastore_round_trip() {
        val forecast = storedForecast().copy(
            sunChange = ChangeSnapshot(
                condition = Condition.SUN,
                currently = false,
                event = Event.STARTS,
                atEpochMillis = reference.plusHours(6).toInstant().toEpochMilli(),
            ),
            rainChange = ChangeSnapshot(condition = Condition.RAIN, currently = false),
        )
        val encoded = cityJson.encodeToString(forecast)
        assertEquals(forecast, cityJson.decodeFromString<CityForecast>(encoded))
    }

    @Test
    fun a_passed_handoff_no_longer_says_then_rain() {
        val snow = Change(true, Event.ENDS, reference.plusHours(1), 60L, Condition.SNOW)
        val rain = Change(false, Event.STARTS, reference.plusHours(1), 60L, Condition.RAIN)
        val sun = Change(false, Event.STARTS, reference.plusHours(6), 360L, Condition.SUN)
        val line = ForecastFormatter.compact(
            changes = listOf(sun, snow, rain),
            phrases = english,
            handoffTo = Condition.RAIN,
            now = reference.plusHours(2),
        )
        assertTrue(line.contains("Rain now"))
        assertFalse(line.contains("then rain"))
    }

    @Test
    fun a_clock_moved_backwards_keeps_the_stored_strings() {
        val forecast = storedForecast().copy(
            sunChange = ChangeSnapshot(
                condition = Condition.SUN,
                currently = false,
                event = Event.STARTS,
                atEpochMillis = reference.plusHours(6).toInstant().toEpochMilli(),
            ),
            rainChange = ChangeSnapshot(condition = Condition.RAIN, currently = false),
        )
        val earlier = reference.minusHours(1).toInstant().toEpochMilli()
        assertEquals(forecast.compactSummary, ForecastText.compact(forecast, earlier, english))
    }

    @Test
    fun only_failed_cities_without_a_forecast_are_unavailable() {
        val forecast = storedForecast()
        assertEquals(
            setOf("hamburg"),
            staleCitiesAfterFetch(setOf("berlin", "hamburg"), mapOf("berlin" to forecast)),
        )
    }

    @Test
    fun stored_rain_again_hint_ages_with_its_absolute_time() {
        val forecast = storedForecast().copy(
            sunChange = ChangeSnapshot(
                condition = Condition.SUN,
                currently = false,
                event = Event.STARTS,
                atEpochMillis = reference.plusHours(6).toInstant().toEpochMilli(),
            ),
            rainChange = ChangeSnapshot(
                condition = Condition.RAIN,
                currently = false,
                event = Event.STARTS,
                atEpochMillis = reference.plusHours(2).toInstant().toEpochMilli(),
            ),
            rainAgainAtMillis = reference.plusHours(3).toInstant().toEpochMilli(),
        )
        val now = reference.plusHours(1).toInstant().toEpochMilli()
        assertEquals(
            "Rain in 1 h (again in 2 h), sun in 5 h",
            ForecastText.compact(forecast, now, english),
        )
    }

    private fun storedForecast() = CityForecast(
        city = "berlin",
        compactSummary = "Sun in 6 h, no rain",
        sunSummary = "Sunshine starts in 6 h.",
        rainSummary = "No rain expected within the forecast horizon.",
        fetchedAtMillis = fetchedAt,
        forecastReferenceMillis = fetchedAt,
    )

    private val noRain = Change(false, null, null, null, Condition.RAIN)

    private val english = ForecastPhrases(
        lowerCaseTrailing = true,
        pair = "%1\$s, %2\$s",
        triple = "%1\$s, %2\$s, %3\$s",
        durationHoursMinutes = "%1\$d h %2\$d min",
        durationHours = "%1\$d h",
        durationMinutes = "%1\$d min",
        sunIn = "Sun in %1\$s",
        sunFor = "Sun for %1\$s",
        sunNow = "Sun now",
        rainIn = "Rain in %1\$s",
        rainFor = "Rain for %1\$s",
        rainNow = "Rain now",
        rainAgain = "%1\$s (again in %2\$s)",
        thenRain = "then rain",
        thenSnow = "then snow",
        noSun = "no sun",
        noRain = "no rain",
        snowIn = "Snow in %1\$s",
        snowFor = "Snow for %1\$s",
        snowNow = "Snow now",
        noSnow = "no snow",
        summarySunStarts = "Sunshine starts in %1\$s (%2\$s).",
        summarySunEnds = "Sunshine ends in %1\$s (%2\$s).",
        summaryRainStarts = "Rain starts in %1\$s (%2\$s).",
        summaryRainEnds = "Rain ends in %1\$s (%2\$s).",
        summaryRainAgain = "%1\$s Then again in %2\$s.",
        summarySunOngoing = "Sunshine is not expected to end within the forecast horizon.",
        summaryRainOngoing = "Rain is not expected to end within the forecast horizon.",
        summaryNoSun = "No sunshine expected within the forecast horizon.",
        summaryNoRain = "No rain expected within the forecast horizon.",
        summarySnowStarts = "Snow starts in %1\$s (%2\$s).",
        summarySnowEnds = "Snow ends in %1\$s (%2\$s).",
        summarySnowOngoing = "Snow is not expected to end within the forecast horizon.",
        summaryNoSnow = "No snow expected within the forecast horizon.",
    )

    private val german = english.copy(
        lowerCaseTrailing = false,
        durationHoursMinutes = "%1\$d Std. %2\$d Min.",
        durationHours = "%1\$d Std.",
        durationMinutes = "%1\$d Min.",
        sunIn = "Sonne in %1\$s",
        sunFor = "Sonne noch %1\$s",
        sunNow = "Sonne jetzt",
        rainIn = "Regen in %1\$s",
        rainNow = "Regen jetzt",
        noSun = "keine Sonne",
        noRain = "kein Regen",
    )
}
