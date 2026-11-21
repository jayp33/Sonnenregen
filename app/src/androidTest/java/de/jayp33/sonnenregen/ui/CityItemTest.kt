package de.jayp33.sonnenregen.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.jayp33.sonnenregen.R
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CityItemTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun city_without_forecast_can_still_be_removed() {
        var removed = false
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val removeLabel = context.getString(R.string.remove)
        val noForecastLabel = context.getString(R.string.no_forecast)

        composeTestRule.setContent {
            MaterialTheme {
                CityItem(
                    label = "Kaputt",
                    index = 0,
                    cityCount = 1,
                    forecast = null,
                    detailsExpanded = true,
                    hidden = false,
                    nowMillis = 0L,
                    onToggleExpanded = {},
                    onToggleNotificationVisibility = {},
                    onMove = {},
                    onRemove = { removed = true },
                )
            }
        }

        composeTestRule.onNodeWithText(noForecastLabel).assertIsDisplayed()
        composeTestRule.onNodeWithText(removeLabel).assertIsDisplayed().performClick()
        assertTrue(removed)
    }

    @Test
    fun controls_are_hidden_until_details_are_expanded() {
        val removeLabel = InstrumentationRegistry.getInstrumentation()
            .targetContext.getString(R.string.remove)

        composeTestRule.setContent {
            MaterialTheme {
                CityItem(
                    label = "Berlin",
                    index = 0,
                    cityCount = 1,
                    forecast = null,
                    detailsExpanded = false,
                    hidden = false,
                    nowMillis = 0L,
                    onToggleExpanded = {},
                    onToggleNotificationVisibility = {},
                    onMove = {},
                    onRemove = {},
                )
            }
        }

        composeTestRule.onNodeWithText(removeLabel).assertDoesNotExist()
    }
}
