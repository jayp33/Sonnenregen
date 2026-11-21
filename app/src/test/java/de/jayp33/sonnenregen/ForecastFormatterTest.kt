package de.jayp33.sonnenregen

import de.jayp33.sonnenregen.domain.Change
import de.jayp33.sonnenregen.domain.Condition
import de.jayp33.sonnenregen.domain.Event
import de.jayp33.sonnenregen.domain.ForecastAnalyzer
import de.jayp33.sonnenregen.domain.RainOutlook
import de.jayp33.sonnenregen.text.ForecastFormatter
import de.jayp33.sonnenregen.text.ForecastPhrases
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.OffsetDateTime

class ForecastFormatterTest {
    @Test
    fun english_compact_orders_by_first_event() {
        assertEquals(
            "Sun in 2 h 10 min, rain in 5 h",
            ForecastFormatter.compact(
                sun = change(Condition.SUN, minutes = 130),
                rain = change(Condition.RAIN, minutes = 300),
                phrases = english,
            ),
        )
        assertEquals(
            "Rain in 30 min, sun in 5 h",
            ForecastFormatter.compact(
                sun = change(Condition.SUN, minutes = 300),
                rain = change(Condition.RAIN, minutes = 30),
                phrases = english,
            ),
        )
    }

    @Test
    fun german_compact_orders_by_first_event() {
        assertEquals(
            "Sonne in 2 Std. 10 Min., kein Regen",
            ForecastFormatter.compact(
                sun = change(Condition.SUN, minutes = 130),
                rain = change(Condition.RAIN),
                phrases = german,
            ),
        )
        assertEquals(
            "Regen in 30 Min., Sonne in 5 Std.",
            ForecastFormatter.compact(
                sun = change(Condition.SUN, minutes = 300),
                rain = change(Condition.RAIN, minutes = 30),
                phrases = german,
            ),
        )
    }

    @Test
    fun no_event_and_current_phrases_are_localized() {
        assertEquals(
            "Sun in 2 h 10 min, no rain",
            ForecastFormatter.compact(
                sun = change(Condition.SUN, minutes = 130),
                rain = change(Condition.RAIN),
                phrases = english,
            ),
        )
        assertEquals(
            "Rain now, no sun",
            ForecastFormatter.compact(
                sun = change(Condition.SUN),
                rain = change(Condition.RAIN, currently = true),
                phrases = english,
            ),
        )
        assertEquals(
            "Regen jetzt, keine Sonne",
            ForecastFormatter.compact(
                sun = change(Condition.SUN),
                rain = change(Condition.RAIN, currently = true),
                phrases = german,
            ),
        )
    }

    @Test
    fun current_conditions_use_for_now_phrasing() {
        assertEquals(
            "Sun for 45 min, rain in 5 h",
            ForecastFormatter.compact(
                sun = change(Condition.SUN, currently = true, minutes = 45),
                rain = change(Condition.RAIN, minutes = 300),
                phrases = english,
            ),
        )
        assertEquals(
            "Sonne noch 45 Min., Regen in 5 Std.",
            ForecastFormatter.compact(
                sun = change(Condition.SUN, currently = true, minutes = 45),
                rain = change(Condition.RAIN, minutes = 300),
                phrases = german,
            ),
        )
    }

    @Test
    fun ties_prefer_sun() {
        assertEquals(
            "Sun in 30 min, rain in 30 min",
            ForecastFormatter.compact(
                sun = change(Condition.SUN, minutes = 30),
                rain = change(Condition.RAIN, minutes = 30),
                phrases = english,
            ),
        )
    }

    @Test
    fun verbose_summaries_are_localized() {
        val page = TestData.page()
        val forecast = ForecastAnalyzer.analyze("berlin", page, page)

        assertEquals(
            "Sunshine starts in 10 h 33 min (2026-11-21 08:00 +0100).",
            ForecastFormatter.verbose(forecast.sun, english),
        )
        assertEquals(
            "Sonne scheint in 10 Std. 33 Min. (2026-11-21 08:00 +0100).",
            ForecastFormatter.verbose(forecast.sun, german),
        )
        assertEquals(
            "Innerhalb des Vorhersagezeitraums ist kein Regen zu erwarten.",
            ForecastFormatter.verbose(change(Condition.RAIN), german),
        )
    }

    @Test
    fun real_forecast_is_compact_in_both_languages() {
        val page = TestData.page()
        val forecast = ForecastAnalyzer.analyze("berlin", page, page)

        assertEquals(
            "Sun in 10 h 33 min, rain in 22 h 33 min",
            ForecastFormatter.compact(forecast, english),
        )
        assertEquals(
            "Sonne in 10 Std. 33 Min., Regen in 22 Std. 33 Min.",
            ForecastFormatter.compact(forecast, german),
        )
    }

    @Test
    fun compact_adds_the_next_rain_inside_the_nowcast_window() {
        assertEquals(
            "Rain for 15 min (again in 45 min), sun in 2 h",
            ForecastFormatter.compact(
                sun = change(Condition.SUN, minutes = 120),
                rain = change(Condition.RAIN, currently = true, minutes = 15),
                phrases = english,
                rainAgainInMinutes = 45,
            ),
        )
        assertEquals(
            "Regen noch 15 Min. (wieder in 45 Min.), Sonne in 2 Std.",
            ForecastFormatter.compact(
                sun = change(Condition.SUN, minutes = 120),
                rain = change(Condition.RAIN, currently = true, minutes = 15),
                phrases = german,
                rainAgainInMinutes = 45,
            ),
        )
    }

    @Test
    fun verbose_rain_mentions_the_later_shower() {
        val forecast = ForecastAnalyzer.analyze("berlin", TestData.page(), TestData.page()).copy(
            rain = change(Condition.RAIN, currently = true, minutes = 15),
            rainOutlook = RainOutlook(againInMinutes = 45),
        )

        assertEquals(
            "Rain ends in 15 min (2026-11-21 08:00 +0100). Then again in 45 min.",
            ForecastFormatter.verboseRain(forecast, english),
        )
    }

    private fun change(
        condition: Condition,
        currently: Boolean = false,
        minutes: Long? = null,
    ) = Change(
        currently = currently,
        event = if (currently) Event.ENDS else if (minutes != null) Event.STARTS else null,
        at = if (minutes != null) OffsetDateTime.parse("2026-11-21T08:00:00+01:00") else null,
        minutes = minutes,
        condition = condition,
    )

    private val english = ForecastPhrases(
        lowerCaseSecond = true,
        pair = "%1\$s, %2\$s",
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
        noSun = "no sun",
        noRain = "no rain",
        summarySunStarts = "Sunshine starts in %1\$s (%2\$s).",
        summarySunEnds = "Sunshine ends in %1\$s (%2\$s).",
        summaryRainStarts = "Rain starts in %1\$s (%2\$s).",
        summaryRainEnds = "Rain ends in %1\$s (%2\$s).",
        summaryRainAgain = "%1\$s Then again in %2\$s.",
        summarySunOngoing = "Sunshine is not expected to end within the forecast horizon.",
        summaryRainOngoing = "Rain is not expected to end within the forecast horizon.",
        summaryNoSun = "No sunshine expected within the forecast horizon.",
        summaryNoRain = "No rain expected within the forecast horizon.",
    )

    private val german = english.copy(
        lowerCaseSecond = false,
        durationHoursMinutes = "%1\$d Std. %2\$d Min.",
        durationHours = "%1\$d Std.",
        durationMinutes = "%1\$d Min.",
        sunIn = "Sonne in %1\$s",
        sunFor = "Sonne noch %1\$s",
        sunNow = "Sonne jetzt",
        rainIn = "Regen in %1\$s",
        rainFor = "Regen noch %1\$s",
        rainNow = "Regen jetzt",
        rainAgain = "%1\$s (wieder in %2\$s)",
        noSun = "keine Sonne",
        noRain = "kein Regen",
        summarySunStarts = "Sonne scheint in %1\$s (%2\$s).",
        summaryNoRain = "Innerhalb des Vorhersagezeitraums ist kein Regen zu erwarten.",
        summaryRainAgain = "%1\$s Danach wieder Regen in %2\$s.",
    )
}
