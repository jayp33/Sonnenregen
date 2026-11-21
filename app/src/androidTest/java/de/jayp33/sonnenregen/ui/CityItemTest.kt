package de.jayp33.sonnenregen.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.jayp33.sonnenregen.R
import de.jayp33.sonnenregen.data.CityForecast
import de.jayp33.sonnenregen.domain.HourCell
import de.jayp33.sonnenregen.domain.HourKind
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
    fun city_label_is_shown_without_a_prefix() {
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

        composeTestRule.onNodeWithText("Berlin").assertIsDisplayed()
        composeTestRule.onNodeWithText("City: Berlin").assertDoesNotExist()
        composeTestRule.onNodeWithText("Ort: Berlin").assertDoesNotExist()
    }

    @Test
    fun nowcast_row_appears_when_it_rains_in_the_90_minutes() {
        val prefix = InstrumentationRegistry.getInstrumentation()
            .targetContext.getString(R.string.nowcast_prefix)

        composeTestRule.setContent {
            MaterialTheme {
                CityItem(
                    label = "Berlin",
                    index = 0,
                    cityCount = 1,
                    forecast = forecastWith(HourKind.RAIN),
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

        composeTestRule.onNodeWithText(prefix).assertIsDisplayed()
    }

    @Test
    fun nowcast_row_is_hidden_without_rain_in_the_90_minutes() {
        val prefix = InstrumentationRegistry.getInstrumentation()
            .targetContext.getString(R.string.nowcast_prefix)

        composeTestRule.setContent {
            MaterialTheme {
                CityItem(
                    label = "Berlin",
                    index = 0,
                    cityCount = 1,
                    forecast = forecastWith(HourKind.CLOUDY),
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

        composeTestRule.onNodeWithText(prefix).assertDoesNotExist()
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

    private fun forecastWith(nowcastKind: HourKind) = CityForecast(
        city = "berlin",
        compactSummary = "Rain for 15 min, sun in 2 h",
        sunSummary = "Sunshine starts in 2 h.",
        rainSummary = "Rain ends in 15 min.",
        fetchedAtMillis = 0L,
        hours = listOf(HourCell(1_000L, HourKind.CLOUDY)),
        nowcastCells = listOf(HourCell(2_000L, nowcastKind, "+01:00")),
    )
}
