package de.jayp33.sonnenregen.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.jayp33.sonnenregen.R
import de.jayp33.sonnenregen.domain.HourCell
import de.jayp33.sonnenregen.domain.HourKind
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.OffsetDateTime

@RunWith(AndroidJUnit4::class)
class HourlyOverviewTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun each_day_row_shows_its_weekday_prefix() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val cells = cellsFrom("2026-11-21T22:00:00+01:00", 4) +
            cellsFrom("2026-11-22T00:00:00+01:00", 3)

        composeTestRule.setContent {
            MaterialTheme {
                HourlyOverview("Berlin", cells)
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.weekday_saturday))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.weekday_sunday))
            .assertIsDisplayed()
    }

    @Test
    fun nowcast_row_has_a_short_prefix_and_quarter_hour_labels() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = context.getString(R.string.nowcast_prefix)
        val cells = (0 until 6).map { index ->
            HourCell(
                startEpochMillis = OffsetDateTime.parse("2026-11-21T21:15:00+01:00")
                    .plusMinutes(15L * index).toInstant().toEpochMilli(),
                kind = if (index == 0) HourKind.RAIN else HourKind.CLOUDY,
                zoneId = "+01:00",
            )
        }

        composeTestRule.setContent {
            MaterialTheme {
                NowcastOverview("Berlin", cells)
            }
        }

        composeTestRule.onNodeWithText(prefix).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(
            context.getString(R.string.hour_description, "Berlin", "21:15", context.getString(R.string.hour_rain)),
        ).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(
            context.getString(R.string.hour_description, "Berlin", "22:30", context.getString(R.string.hour_cloudy)),
        ).assertIsDisplayed()
    }

    private fun cellsFrom(start: String, count: Int): List<HourCell> {
        val first = OffsetDateTime.parse(start)
        return (0 until count).map { index ->
            HourCell(
                startEpochMillis = first.plusHours(index.toLong()).toInstant().toEpochMilli(),
                kind = HourKind.SUN,
                zoneId = "+01:00",
            )
        }
    }
}
