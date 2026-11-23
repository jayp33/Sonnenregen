package de.jayp33.sonnenregen

import android.content.ComponentName
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.jayp33.sonnenregen.ui.MainActivity
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/** Companion apps such as Mi Fitness list only apps that declare a launcher icon. */
@RunWith(AndroidJUnit4::class)
class AppIconTest {
    @Test
    fun launcher_icon_is_declared_for_the_app_and_its_launcher_activity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.packageManager

        val app = manager.getApplicationInfo(context.packageName, 0)
        val activity = manager.getActivityInfo(ComponentName(context, MainActivity::class.java), 0)

        assertNotEquals(0, app.icon)
        assertNotEquals(0, activity.icon)
        assertNotNull(app.loadIcon(manager))
        assertNotNull(activity.loadIcon(manager))
    }
}
