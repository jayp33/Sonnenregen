package de.jayp33.sonnenregen

import de.jayp33.sonnenregen.notification.BatteryOptimizations
import org.junit.Assert.assertEquals
import org.junit.Test

class BatteryOptimizationsTest {
    @Test
    fun request_uri_targets_the_app_package() {
        assertEquals(
            "package:de.jayp33.sonnenregen",
            BatteryOptimizations.packageUri("de.jayp33.sonnenregen"),
        )
    }
}
