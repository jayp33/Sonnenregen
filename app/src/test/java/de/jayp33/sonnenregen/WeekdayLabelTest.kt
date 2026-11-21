package de.jayp33.sonnenregen

import de.jayp33.sonnenregen.ui.weekdayLabel
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek

class WeekdayLabelTest {
    @Test
    fun every_day_maps_to_its_own_label() {
        assertEquals(R.string.weekday_monday, DayOfWeek.MONDAY.weekdayLabel())
        assertEquals(R.string.weekday_tuesday, DayOfWeek.TUESDAY.weekdayLabel())
        assertEquals(R.string.weekday_wednesday, DayOfWeek.WEDNESDAY.weekdayLabel())
        assertEquals(R.string.weekday_thursday, DayOfWeek.THURSDAY.weekdayLabel())
        assertEquals(R.string.weekday_friday, DayOfWeek.FRIDAY.weekdayLabel())
        assertEquals(R.string.weekday_saturday, DayOfWeek.SATURDAY.weekdayLabel())
        assertEquals(R.string.weekday_sunday, DayOfWeek.SUNDAY.weekdayLabel())
    }
}
