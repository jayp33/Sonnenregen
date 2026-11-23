package de.jayp33.sonnenregen.notification

import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BatteryOptimizationsIntentTest {
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun request_dialog_targets_this_package() {
        val intent = BatteryOptimizations.requestIntent(context)

        assertEquals(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, intent.action)
        assertEquals("package:${context.packageName}", intent.dataString)
        assertEquals(context.packageName, intent.data?.schemeSpecificPart)
    }

    @Test
    fun fallback_opens_the_system_settings_list() {
        assertEquals(
            Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS,
            BatteryOptimizations.settingsIntent().action,
        )
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        assertEquals(
            power.isIgnoringBatteryOptimizations(context.packageName),
            BatteryOptimizations.isIgnoring(context),
        )
    }
}
