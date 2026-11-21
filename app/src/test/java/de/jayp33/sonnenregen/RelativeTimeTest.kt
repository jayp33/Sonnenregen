package de.jayp33.sonnenregen

import de.jayp33.sonnenregen.text.AgeUnit
import de.jayp33.sonnenregen.text.relativeAge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RelativeTimeTest {
    @Test
    fun missing_timestamp_has_no_age() {
        assertNull(relativeAge(0L, now))
    }

    @Test
    fun under_a_minute_is_just_now() {
        assertEquals(AgeUnit.JUST_NOW, relativeAge(now - 59_000L, now)?.unit)
    }

    @Test
    fun minutes_hours_and_days_use_coarse_buckets() {
        assertEquals(AgeUnit.MINUTES to 12L, relativeAge(now - 12 * minute, now)?.let { it.unit to it.value })
        assertEquals(AgeUnit.HOURS to 2L, relativeAge(now - 2 * hour, now)?.let { it.unit to it.value })
        assertEquals(AgeUnit.DAYS to 1L, relativeAge(now - 24 * hour, now)?.let { it.unit to it.value })
    }

    @Test
    fun future_timestamps_are_clamped_to_just_now() {
        assertEquals(AgeUnit.JUST_NOW, relativeAge(now + hour, now)?.unit)
    }

    private companion object {
        const val now = 1_000_000_000L
        const val minute = 60_000L
        const val hour = 60 * minute
    }
}
