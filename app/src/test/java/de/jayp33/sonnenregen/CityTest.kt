package de.jayp33.sonnenregen

import de.jayp33.sonnenregen.domain.displayCity
import de.jayp33.sonnenregen.domain.isValidCitySlug
import de.jayp33.sonnenregen.domain.normalizeCity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CityTest {
    @Test
    fun normalizes_user_input() {
        assertEquals("berlin", normalizeCity("  Berlin  "))
        assertEquals("frankfurt-am-main", normalizeCity("Frankfurt-Am-Main"))
    }

    @Test
    fun capitalizes_only_the_first_letter_for_display() {
        assertEquals("Berlin", displayCity("berlin"))
        assertEquals("Frankfurt-am-main", displayCity("frankfurt-am-main"))
        assertEquals("", displayCity(""))
    }

    @Test
    fun accepts_wetteronline_style_slugs() {
        assertTrue(isValidCitySlug("berlin"))
        assertTrue(isValidCitySlug("frankfurt-am-main"))
        assertTrue(isValidCitySlug("a1"))
    }

    @Test
    fun rejects_unsafe_or_empty_slugs() {
        assertFalse(isValidCitySlug(""))
        assertFalse(isValidCitySlug("Berlin"))
        assertFalse(isValidCitySlug("berlin mitte"))
        assertFalse(isValidCitySlug("../berlin"))
        assertFalse(isValidCitySlug("berlin?x=1"))
        assertFalse(isValidCitySlug("berlin--mitte"))
    }
}
