package de.jayp33.sonnenregen.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
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
    fun nowcast_row_appears_when_the_sun_shines_in_the_90_minutes() {
        val prefix = InstrumentationRegistry.getInstrumentation()
            .targetContext.getString(R.string.nowcast_prefix)

        composeTestRule.setContent {
            MaterialTheme {
                CityItem(
                    label = "Berlin",
                    index = 0,
                    cityCount = 1,
                    forecast = forecastWith(HourKind.SUN),
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
    fun nowcast_row_appears_when_partly_sunny_in_the_90_minutes() {
        val prefix = InstrumentationRegistry.getInstrumentation()
            .targetContext.getString(R.string.nowcast_prefix)

        composeTestRule.setContent {
            MaterialTheme {
                CityItem(
                    label = "Berlin",
                    index = 0,
                    cityCount = 1,
                    forecast = forecastWith(HourKind.PARTLY_SUN),
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
    fun nowcast_row_appears_when_it_snows_in_the_90_minutes() {
        val prefix = InstrumentationRegistry.getInstrumentation()
            .targetContext.getString(R.string.nowcast_prefix)

        composeTestRule.setContent {
            MaterialTheme {
                CityItem(
                    label = "Berlin",
                    index = 0,
                    cityCount = 1,
                    forecast = forecastWith(HourKind.SNOW),
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
    fun nowcast_row_is_hidden_without_sun_or_rain_in_the_90_minutes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = context.getString(R.string.nowcast_prefix)
        val staleLabel = context.getString(R.string.nowcast_stale)

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
        composeTestRule.onNodeWithText(staleLabel).assertDoesNotExist()
    }

    @Test
    fun past_hours_are_blank_but_future_hours_keep_their_slots() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val start = OffsetDateTime.parse("2026-11-21T00:00:00+01:00")
        val hours = (0 until 24).map { hour ->
            HourCell(
                startEpochMillis = start.plusHours(hour.toLong()).toInstant().toEpochMilli(),
                kind = HourKind.SUN,
                zoneId = "+01:00",
            )
        }
        val forecast = forecastWith(HourKind.CLOUDY).copy(
            hours = hours,
            nowcastCells = emptyList(),
        )
        val now = start.plusHours(10).plusMinutes(30).toInstant().toEpochMilli()
        val description = { time: String ->
            context.getString(
                R.string.hour_description,
                "Berlin",
                time,
                context.getString(R.string.hour_sun),
            )
        }

        composeTestRule.setContent {
            MaterialTheme {
                CityItem(
                    label = "Berlin",
                    index = 0,
                    cityCount = 1,
                    forecast = forecast,
                    detailsExpanded = false,
                    hidden = false,
                    nowMillis = now,
                    onToggleExpanded = {},
                    onToggleNotificationVisibility = {},
                    onMove = {},
                    onRemove = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(
            "${WeatherRowTags.HOUR_ROW}0", useUnmergedTree = true,
        ).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(description("09:00")).assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription(description("10:00")).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.hourly_stale)).assertDoesNotExist()
    }

    @Test
    fun fully_elapsed_hourly_grid_shows_an_outdated_hint() {
        val staleLabel = InstrumentationRegistry.getInstrumentation()
            .targetContext.getString(R.string.hourly_stale)
        val cellStart = OffsetDateTime.parse("2026-11-21T09:00:00+01:00")
            .toInstant().toEpochMilli()
        val forecast = forecastWith(HourKind.CLOUDY).copy(
            hours = listOf(HourCell(cellStart, HourKind.SUN, "+01:00")),
            nowcastCells = emptyList(),
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
                    nowMillis = cellStart + 60 * 60_000L + 1,
                    onToggleExpanded = {},
                    onToggleNotificationVisibility = {},
                    onMove = {},
                    onRemove = {},
                )
            }
        }

        composeTestRule.onNodeWithText(staleLabel).assertIsDisplayed()
        composeTestRule.onNodeWithTag(
            "${WeatherRowTags.HOUR_ROW}0", useUnmergedTree = true,
        ).assertDoesNotExist()
    }

    @Test
    fun stale_nowcast_row_shows_an_outdated_hint_when_the_forecast_cannot_refresh() {
        val staleLabel = InstrumentationRegistry.getInstrumentation()
            .targetContext.getString(R.string.nowcast_stale)
        val cellStart = OffsetDateTime.parse("2026-11-21T21:00:00+01:00")
            .toInstant().toEpochMilli()
        val forecast = forecastWith(HourKind.RAIN).copy(
            nowcastCells = listOf(HourCell(cellStart, HourKind.RAIN, "+01:00")),
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
                    nowMillis = cellStart + 16 * 60_000L,
                    onToggleExpanded = {},
                    onToggleNotificationVisibility = {},
                    onMove = {},
                    onRemove = {},
                )
            }
        }

        composeTestRule.onNodeWithText(staleLabel).assertIsDisplayed()
    }

    @Test
    fun current_nowcast_cell_stays_visible_when_the_forecast_cannot_refresh() {
        val prefix = InstrumentationRegistry.getInstrumentation()
            .targetContext.getString(R.string.nowcast_prefix)
        val cellStart = OffsetDateTime.parse("2026-11-21T21:00:00+01:00")
            .toInstant().toEpochMilli()
        val forecast = forecastWith(HourKind.RAIN).copy(
            nowcastCells = listOf(HourCell(cellStart, HourKind.RAIN, "+01:00")),
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
                    nowMillis = cellStart + 5 * 60_000L,
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
    fun alert_switches_default_off_and_report_toggles() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var sunEnabled: Boolean? = null
        var rainEnabled: Boolean? = null
        var snowEnabled: Boolean? = null

        composeTestRule.setContent {
            MaterialTheme {
                CityItem(
                    label = "Berlin",
                    index = 0,
                    cityCount = 1,
                    forecast = null,
                    detailsExpanded = true,
                    hidden = false,
                    onToggleExpanded = {},
                    onToggleNotificationVisibility = {},
                    onToggleSunAlert = { sunEnabled = it },
                    onToggleRainAlert = { rainEnabled = it },
                    onToggleSnowAlert = { snowEnabled = it },
                    onMove = {},
                    onRemove = {},
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.sun_alert)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.rain_alert)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.snow_alert)).assertIsDisplayed()
        composeTestRule.onNodeWithTag(WeatherControlTags.SUN_ALERT).assertIsOff().performClick()
        composeTestRule.onNodeWithTag(WeatherControlTags.RAIN_ALERT).assertIsOff().performClick()
        composeTestRule.onNodeWithTag(WeatherControlTags.SNOW_ALERT).assertIsOff().performClick()

        assertEquals(true, sunEnabled)
        assertEquals(true, rainEnabled)
        assertEquals(true, snowEnabled)
    }

    @Test
    fun widget_toggle_reports_selection_and_deselection() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var selected: Boolean? = null
        val widgetSelected = mutableStateOf(false)

        composeTestRule.setContent {
            MaterialTheme {
                CityItem(
                    label = "Berlin",
                    index = 0,
                    cityCount = 1,
                    forecast = null,
                    detailsExpanded = true,
                    hidden = false,
                    widgetSelected = widgetSelected.value,
                    onToggleExpanded = {},
                    onToggleNotificationVisibility = {},
                    onToggleWidget = { selected = it },
                    onMove = {},
                    onRemove = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText(context.getString(R.string.show_in_widget))
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(WeatherControlTags.WIDGET).assertIsOff().performClick()
        assertEquals(true, selected)

        composeTestRule.runOnIdle { widgetSelected.value = true }
        composeTestRule.onNodeWithTag(WeatherControlTags.WIDGET).assertIsOn().performClick()
        assertEquals(false, selected)
    }

    @Test
    fun lead_time_selectors_appear_only_for_enabled_alerts() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var sunLead: Int? = null
        var snowLead: Int? = null

        composeTestRule.setContent {
            MaterialTheme {
                CityItem(
                    label = "Berlin",
                    index = 0,
                    cityCount = 1,
                    forecast = null,
                    detailsExpanded = true,
                    hidden = false,
                    sunAlert = true,
                    rainAlert = false,
                    snowAlert = true,
                    sunLeadMinutes = 30,
                    snowLeadMinutes = 15,
                    onToggleExpanded = {},
                    onToggleNotificationVisibility = {},
                    onSetSunLeadMinutes = { sunLead = it },
                    onSetSnowLeadMinutes = { snowLead = it },
                    onMove = {},
                    onRemove = {},
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.sun_lead, 30)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.rain_lead, 0)).assertDoesNotExist()
        composeTestRule.onNodeWithText(context.getString(R.string.snow_lead, 15)).assertIsDisplayed()
        composeTestRule.onNodeWithTag(WeatherControlTags.SUN_LEAD).performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.lead_minutes, 15)).performClick()
        composeTestRule.onNodeWithTag(WeatherControlTags.SNOW_LEAD).performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.lead_minutes, 30)).performClick()

        assertEquals(15, sunLead)
        assertEquals(30, snowLead)
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

    @Test
    fun website_link_invokes_the_callback_from_expanded_details() {
        var opened = false
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        composeTestRule.setContent {
            MaterialTheme {
                CityItem(
                    label = "Bützow",
                    index = 0,
                    cityCount = 1,
                    forecast = null,
                    detailsExpanded = true,
                    hidden = false,
                    onToggleExpanded = {},
                    onToggleNotificationVisibility = {},
                    onOpenWebsite = { opened = true },
                    onMove = {},
                    onRemove = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(WeatherControlTags.OPEN_WEBSITE).assertIsDisplayed()
        composeTestRule
            .onNodeWithText(context.getString(R.string.open_on_wetteronline))
            .performClick()
        assertTrue(opened)
    }

    @Test
    fun expanded_details_show_a_snow_line_when_snow_is_expected() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val snowSummary = "Snow starts in 1 h."
        val forecast = forecastWith(HourKind.SNOW).copy(snowSummary = snowSummary)

        composeTestRule.setContent {
            MaterialTheme {
                CityItem(
                    label = "Berlin",
                    index = 0,
                    cityCount = 1,
                    forecast = forecast,
                    detailsExpanded = true,
                    hidden = false,
                    nowMillis = 0L,
                    onToggleExpanded = {},
                    onToggleNotificationVisibility = {},
                    onMove = {},
                    onRemove = {},
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.snow, snowSummary))
            .assertIsDisplayed()
    }

    @Test
    fun expanded_details_hide_the_snow_line_when_no_snow_is_expected() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // forecastWith leaves snowSummary blank, so no snow line should render.
        val forecast = forecastWith(HourKind.CLOUDY)

        composeTestRule.setContent {
            MaterialTheme {
                CityItem(
                    label = "Berlin",
                    index = 0,
                    cityCount = 1,
                    forecast = forecast,
                    detailsExpanded = true,
                    hidden = false,
                    nowMillis = 0L,
                    onToggleExpanded = {},
                    onToggleNotificationVisibility = {},
                    onMove = {},
                    onRemove = {},
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.rain, forecast.rainSummary))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.snow, ""))
            .assertDoesNotExist()
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
