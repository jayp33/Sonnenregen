package de.jayp33.sonnenregen.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.jayp33.sonnenregen.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BatteryOptimizationControlTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun button_is_shown_and_clicked_while_optimizations_are_active() {
        var requests = 0
        composeTestRule.setContent {
            MaterialTheme {
                BatteryOptimizationControl(ignoring = false, onRequest = { requests++ })
            }
        }

        composeTestRule.onNodeWithTag(WeatherControlTags.BATTERY_OPTIMIZATIONS)
            .assertIsDisplayed()
            .performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.ignore_battery_optimizations))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.battery_optimizations_ignored))
            .assertDoesNotExist()

        assertEquals(1, requests)
    }

    @Test
    fun status_replaces_the_button_when_exempt() {
        var requests = 0
        composeTestRule.setContent {
            MaterialTheme {
                BatteryOptimizationControl(ignoring = true, onRequest = { requests++ })
            }
        }

        composeTestRule.onNodeWithTag(WeatherControlTags.BATTERY_OPTIMIZATIONS)
            .assertDoesNotExist()
        composeTestRule.onNodeWithText(context.getString(R.string.battery_optimizations_ignored))
            .assertIsDisplayed()

        assertEquals(0, requests)
    }
}
