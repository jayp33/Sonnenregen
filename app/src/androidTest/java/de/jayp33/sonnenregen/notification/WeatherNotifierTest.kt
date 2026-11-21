package de.jayp33.sonnenregen.notification

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
    fun only_the_refresh_action_remains() {
        val notification = WeatherNotifier.buildNotification(context, emptyList())

        val actions = notification.actions ?: emptyArray()
        assertEquals(1, actions.size)
        assertEquals(context.getString(R.string.refresh), actions[0].title.toString())
    }
}
