package de.jayp33.sonnenregen.domain

const val DEFAULT_CITY = "berlin"

private const val SEGMENT = "[a-z0-9]+(?:-[a-z0-9]+)*"

/**
 * WetterOnline sometimes disambiguates small places with a path such as
 * `michelbach-an-der-bilz/gschlachtenbretzingen`. Each segment is still a plain
 * lowercase slug, so no traversal or query characters can slip into a URL.
 */
private val CITY_REGEX = Regex("^$SEGMENT(?:/$SEGMENT)*$")

/** Accept user input such as " Berlin " and return the WetterOnline slug. */
fun normalizeCity(input: String): String = input.trim().lowercase()

/** Guard URL construction against unexpected characters. */
fun isValidCitySlug(value: String): Boolean = CITY_REGEX.matches(value)

/** Slugs stay lowercase for URLs; capitalise every path segment for display. */
fun displayCity(slug: String): String = slug
    .split('/')
    .joinToString("/") { it.replaceFirstChar { char -> char.uppercase() } }

/**
 * WetterOnline occasionally serves umlauts as double-encoded replacement
 * characters (e.g. "Bï¿½tzow"). The URL slug is still intact, so it is a more
 * reliable source of the spelling than the mangled heading.
 */
private val mojibakeRegex = Regex("\uFFFD|ï¿½|Ã[¤¶¼]")

private fun nameFromSlug(slug: String): String = slug
    .split('/')
    .joinToString("/") { segment ->
        segment.split('-').joinToString(" ") { word ->
            word.replace("ae", "ä")
                .replace("oe", "ö")
                .replace("ue", "ü")
                .replace("ss", "ß")
                .replaceFirstChar { char -> char.uppercase() }
        }
    }

/** Prefer the source page's name, but rebuild it from the slug when it is mangled. */
fun displayNameFor(slug: String, providerName: String?): String {
    val value = providerName?.trim().orEmpty()
    return when {
        value.isEmpty() -> displayCity(slug)
        mojibakeRegex.containsMatchIn(value) -> nameFromSlug(slug)
        else -> value
    }
}

/** Public WetterOnline page with the map view, useful for verifying a slug. */
fun cityWebsiteUrl(slug: String): String = "$BASE_URL/wetter/$slug"
