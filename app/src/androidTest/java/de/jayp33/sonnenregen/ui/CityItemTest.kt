package de.jayp33.sonnenregen.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.jayp33.sonnenregen.R
import de.jayp33.sonnenregen.data.CityForecast
import de.jayp33.sonnenregen.domain.HourCell
import de.jayp33.sonnenregen.domain.HourKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.OffsetDateTime

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
    fun hidden_city_shows_an_eye_icon_instead_of_the_text() {
        val hiddenLabel = InstrumentationRegistry.getInstrumentation()
            .targetContext.getString(R.string.hidden_indicator)

        composeTestRule.setContent {
            MaterialTheme {
                CityItem(
                    label = "Berlin",
                    index = 0,
                    cityCount = 1,
                    forecast = null,
                    detailsExpanded = false,
                    hidden = true,
                    nowMillis = 0L,
                    onToggleExpanded = {},
                    onToggleNotificationVisibility = {},
                    onMove = {},
                    onRemove = {},
                )
            }
        }

        composeTestRule.onNodeWithContentDescription(hiddenLabel).assertIsDisplayed()
        composeTestRule.onNodeWithText(hiddenLabel).assertDoesNotExist()
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

    @Test
    fun hour_rows_are_tighter_than_the_gap_before_the_nowcast_row() {
        val first = OffsetDateTime.parse("2026-11-21T00:00:00+01:00")
        val hours = (0 until 3).flatMap { day ->
            (0 until 24).map { hour ->
                HourCell(
                    startEpochMillis = first.plusDays(day.toLong()).plusHours(hour.toLong())
                        .toInstant().toEpochMilli(),
                    kind = HourKind.CLOUDY,
                    zoneId = "+01:00",
                )
            }
        }
        val forecast = CityForecast(
            city = "berlin",
            compactSummary = "",
            sunSummary = "",
            rainSummary = "",
            fetchedAtMillis = 0L,
            hours = hours,
            nowcastCells = listOf(
                HourCell(
                    startEpochMillis = OffsetDateTime.parse("2026-11-23T21:15:00+01:00")
                        .toInstant().toEpochMilli(),
                    kind = HourKind.RAIN,
                    zoneId = "+01:00",
                ),
            ),
        )

        composeTestRule.setContent {
            MaterialTheme {
                CityItem(
                    label = "Berlin",
                    index = 0,
                    cityCount = 1,
                    forecast = forecast,
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

        val firstRow = composeTestRule.onNodeWithTag(
            "${WeatherRowTags.HOUR_ROW}0", useUnmergedTree = true,
        ).getBoundsInRoot()
        val secondRow = composeTestRule.onNodeWithTag(
            "${WeatherRowTags.HOUR_ROW}1", useUnmergedTree = true,
        ).getBoundsInRoot()
        val thirdRow = composeTestRule.onNodeWithTag(
            "${WeatherRowTags.HOUR_ROW}2", useUnmergedTree = true,
        ).getBoundsInRoot()
        val nowcastRow = composeTestRule.onNodeWithTag(
            WeatherRowTags.NOWCAST_ROW, useUnmergedTree = true,
        ).getBoundsInRoot()

        val hourGap = secondRow.top - firstRow.bottom
        val nowcastGap = nowcastRow.top - thirdRow.bottom

        assertEquals(WeatherRowSpacing.betweenHourRows.value, hourGap.value, 0.5f)
        assertEquals((4.dp + WeatherRowSpacing.beforeNowcast).value, nowcastGap.value, 0.5f)
        assertTrue(nowcastGap.value > hourGap.value)
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
