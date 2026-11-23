package de.jayp33.sonnenregen.notification

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.jayp33.sonnenregen.R
import de.jayp33.sonnenregen.data.CityStatus
import de.jayp33.sonnenregen.ui.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.OffsetDateTime

@RunWith(AndroidJUnit4::class)
class WeatherNotifierTest {
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun tapping_the_notification_opens_main_activity() {
        val notification = WeatherNotifier.buildNotification(
            context,
            listOf(CityStatus("berlin", "Berlin", "Sun in 2 h", 20L, false)),
        )
        val expected = PendingIntent.getActivity(
            context,
            WeatherNotifier.OPEN_REQUEST_CODE,
            WeatherNotifier.openAppIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        assertEquals(expected, notification.contentIntent)
    }

    @Test
    fun opening_reuses_an_existing_main_activity() {
        val intent = WeatherNotifier.openAppIntent(context)

        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
    }

    @Test
    fun alert_text_uses_the_condition_and_city() {
        val sun = WeatherNotifier.buildAlertNotification(
            context,
            WeatherAlert("berlin", "Berlin", AlertKind.SUN),
        )
        val rain = WeatherNotifier.buildAlertNotification(
            context,
            WeatherAlert("berlin", "Berlin", AlertKind.RAIN),
        )

        assertEquals(
            context.getString(R.string.alert_sun, "Berlin"),
            sun.extras.getCharSequence(Notification.EXTRA_TEXT).toString(),
        )
        assertEquals(
            context.getString(R.string.alert_rain, "Berlin"),
            rain.extras.getCharSequence(Notification.EXTRA_TEXT).toString(),
        )
        assertTrue(sun.flags and Notification.FLAG_AUTO_CANCEL != 0)
    }

    @Test
    fun lead_time_alerts_announce_the_start_in_the_citys_timezone() {
        val sunStart = OffsetDateTime.parse("2026-11-23T11:30:00Z").toInstant().toEpochMilli()
        val sun = WeatherNotifier.buildAlertNotification(
            context,
            WeatherAlert(
                city = "berlin",
                displayName = "Berlin",
                kind = AlertKind.SUN,
                leadMinutes = 15,
                eventAtMillis = sunStart,
                zoneId = "+01:00",
            ),
            nowMillis = sunStart - 15 * 60_000L,
        )
        val rainStart = OffsetDateTime.parse("2026-11-23T14:15:00Z").toInstant().toEpochMilli()
        val rain = WeatherNotifier.buildAlertNotification(
            context,
            WeatherAlert(
                city = "hamburg",
                displayName = "Hamburg",
                kind = AlertKind.RAIN,
                leadMinutes = 60,
                eventAtMillis = rainStart,
                zoneId = "+01:00",
            ),
            nowMillis = rainStart - 60 * 60_000L,
        )

        assertEquals(
            context.getString(R.string.alert_sun_in, "Berlin", 15, "12:30"),
            sun.extras.getCharSequence(Notification.EXTRA_TEXT).toString(),
        )
        assertEquals(
            context.getString(R.string.alert_rain_in, "Hamburg", 60, "15:15"),
            rain.extras.getCharSequence(Notification.EXTRA_TEXT).toString(),
        )
    }

    @Test
    fun a_zero_lead_keeps_the_present_tense_even_with_an_event_time() {
        val event = OffsetDateTime.parse("2026-11-23T11:30:00Z").toInstant().toEpochMilli()
        val notification = WeatherNotifier.buildAlertNotification(
            context,
            WeatherAlert(
                city = "berlin",
                displayName = "Berlin",
                kind = AlertKind.SUN,
                eventAtMillis = event,
                zoneId = "+01:00",
            ),
            nowMillis = event,
        )

        assertEquals(
            context.getString(R.string.alert_sun, "Berlin"),
            notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString(),
        )
    }

    @Test
    fun sun_and_rain_alerts_get_distinct_ids() {
        val sun = WeatherAlert("berlin", "Berlin", AlertKind.SUN)
        val rain = WeatherAlert("berlin", "Berlin", AlertKind.RAIN)

        assertEquals(false, WeatherNotifier.alertNotificationId(sun) == WeatherNotifier.alertNotificationId(rain))
        assertEquals(true, WeatherNotifier.alertNotificationId(sun) > 0)
    }

    @Test
    fun only_the_refresh_action_remains() {
        val notification = WeatherNotifier.buildNotification(context, emptyList())

        val actions = notification.actions ?: emptyArray()
        assertEquals(1, actions.size)
        assertEquals(context.getString(R.string.refresh), actions[0].title.toString())
    }
}
