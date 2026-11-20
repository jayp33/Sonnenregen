package de.jayp33.sonnenregen

import de.jayp33.sonnenregen.domain.Change
import de.jayp33.sonnenregen.domain.Event
import de.jayp33.sonnenregen.domain.ForecastAnalyzer
import de.jayp33.sonnenregen.domain.NotificationText
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationTextTest {
    @Test
    fun sun_first_when_sun_happens_first() {
        assertEquals(
            "Sun in 2 h 10 min, rain in 5 h",
            NotificationText.compact(
                sun = change(minutes = 130),
                rain = change(label = "Rain", minutes = 300),
            ),
        )
    }

    @Test
    fun rain_first_when_rain_happens_first() {
        assertEquals(
            "Rain in 30 min, sun in 5 h",
            NotificationText.compact(
                sun = change(minutes = 300),
                rain = change(label = "Rain", minutes = 30),
            ),
        )
    }

    @Test
    fun no_event_is_phrased_as_no_sun_or_no_rain() {
        assertEquals(
            "Sun in 2 h 10 min, no rain",
            NotificationText.compact(
                sun = change(minutes = 130),
                rain = change(label = "Rain"),
            ),
        )
    }

    @Test
    fun current_conditions_use_for_now() {
        assertEquals(
            "Rain now, no sun",
            NotificationText.compact(
                sun = change(),
                rain = change(label = "Rain", currently = true),
            ),
        )
        assertEquals(
            "Sun for 45 min, rain in 5 h",
            NotificationText.compact(
                sun = change(currently = true, minutes = 45),
                rain = change(label = "Rain", minutes = 300),
            ),
        )
    }

    @Test
    fun ties_prefer_sun() {
        assertEquals(
            "Sun in 30 min, rain in 30 min",
            NotificationText.compact(
                sun = change(minutes = 30),
                rain = change(label = "Rain", minutes = 30),
            ),
        )
    }

    @Test
    fun real_forecast_is_compact() {
        val page = TestData.page()
        val forecast = ForecastAnalyzer.analyze("berlin", page, page)

        assertEquals(
            "Sun in 10 h 33 min, rain in 22 h 33 min",
            NotificationText.compact(forecast),
        )
    }

    private fun change(
        label: String = "Sun",
        currently: Boolean = false,
        minutes: Long? = null,
    ) = Change(
        currently = currently,
        event = if (currently) Event.ENDS else if (minutes != null) Event.STARTS else null,
        at = null,
        minutes = minutes,
        label = label,
        summary = "",
    )
}
