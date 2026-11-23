package de.jayp33.sonnenregen

import de.jayp33.sonnenregen.domain.cityWebsiteUrl
import de.jayp33.sonnenregen.domain.displayCity
import de.jayp33.sonnenregen.domain.displayNameFor
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
        assertEquals(
            "Michelbach-an-der-bilz/Gschlachtenbretzingen",
            displayCity("michelbach-an-der-bilz/gschlachtenbretzingen"),
        )
        assertEquals("", displayCity(""))
    }

    @Test
    fun rebuilds_mangled_names_from_the_slug() {
        assertEquals("Bützow", displayNameFor("buetzow", "Bï¿½tzow"))
        assertEquals("Großenhain", displayNameFor("grossenhain", "Groï¿½enhain"))
        assertEquals("Bad München", displayNameFor("bad-muenchen", "Bad Mï¿½nchen"))
        assertEquals("Lübeck", displayNameFor("luebeck", "L\uFFFDbeck"))
    }

    @Test
    fun keeps_clean_provider_names_and_falls_back_for_missing_ones() {
        assertEquals("Frankfurt (Oder)", displayNameFor("frankfurt-oder", "Frankfurt (Oder)"))
        assertEquals("Neuenburg", displayNameFor("neuenburg", "Neuenburg"))
        assertEquals("Berlin", displayNameFor("berlin", null))
        assertEquals("Berlin", displayNameFor("berlin", ""))
    }

    @Test
    fun builds_the_public_map_link_from_the_slug() {
        assertEquals("https://www.wetteronline.de/wetter/berlin", cityWebsiteUrl("berlin"))
        assertEquals(
            "https://www.wetteronline.de/wetter/michelbach-an-der-bilz/gschlachtenbretzingen",
            cityWebsiteUrl("michelbach-an-der-bilz/gschlachtenbretzingen"),
        )
    }

    @Test
    fun accepts_wetteronline_style_slugs() {
        assertTrue(isValidCitySlug("berlin"))
        assertTrue(isValidCitySlug("frankfurt-am-main"))
        assertTrue(isValidCitySlug("a1"))
        assertTrue(isValidCitySlug("michelbach-an-der-bilz/gschlachtenbretzingen"))
    }

    @Test
    fun rejects_unsafe_or_empty_slugs() {
        assertFalse(isValidCitySlug(""))
        assertFalse(isValidCitySlug("Berlin"))
        assertFalse(isValidCitySlug("berlin mitte"))
        assertFalse(isValidCitySlug("../berlin"))
        assertFalse(isValidCitySlug("berlin?x=1"))
        assertFalse(isValidCitySlug("berlin--mitte"))
        assertFalse(isValidCitySlug("/berlin"))
        assertFalse(isValidCitySlug("berlin/"))
        assertFalse(isValidCitySlug("berlin//mitte"))
        assertFalse(isValidCitySlug("berlin/mitte?x=1"))
    }
}
