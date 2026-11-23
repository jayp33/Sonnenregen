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
private val umlautDigraphs = listOf("ae" to "ä", "oe" to "ö", "ue" to "ü", "ss" to "ß")
private const val MANGLED = '\u0000'

private fun nameFromSlug(slug: String): String = slug
    .split('/')
    .joinToString("/") { segment ->
        segment.split('-').joinToString(" ") { word ->
            umlautDigraphs.fold(word) { text, (digraph, letter) ->
                text.replace(digraph, letter)
            }.replaceFirstChar { char -> char.uppercase() }
        }
    }

/** The slug's spelling with digraphs expanded, reduced to comparable letters. */
private fun expectedSkeleton(slug: String): String {
    val expanded = umlautDigraphs.fold(slug.lowercase()) { text, (digraph, letter) ->
        text.replace(digraph, letter)
    }
    return expanded.filter { it.isLetter() }
}

/** The page name reduced to letters, with each broken umlaut kept as one slot. */
private fun actualSkeleton(name: String): String = buildString {
    var cursor = 0
    mojibakeRegex.findAll(name).forEach { match ->
        append(name.substring(cursor, match.range.first).filterLetters())
        append(MANGLED)
        cursor = match.range.last + 1
    }
    append(name.substring(cursor).filterLetters())
}

private fun String.filterLetters(): String = lowercase().filter { it.isLetter() }

/**
 * Replace only the broken characters, keeping the page's punctuation and casing:
 * "Waren (Mï¿½ritz)" + "waren-mueritz" becomes "Waren (Müritz)".
 */
private fun repairName(name: String, slug: String): String {
    val expected = expectedSkeleton(slug)
    val actual = actualSkeleton(name)
    if (actual.length == expected.length) {
        val replacements = actual.indices.mapNotNull { index ->
            if (actual[index] == MANGLED) expected[index] else null
        }
        if (replacements.isNotEmpty() && replacements.none { it == MANGLED }) {
            var index = 0
            return mojibakeRegex.replace(name) { replacements[index++].toString() }
        }
    }
    // No usable alignment (e.g. a legacy slug without the umlaut digraph).
    return nameFromSlug(slug)
}

/** Prefer the source page's name, but repair broken umlauts using the slug. */
fun displayNameFor(slug: String, providerName: String?): String {
    val value = providerName?.trim().orEmpty()
    return when {
        value.isEmpty() -> displayCity(slug)
        mojibakeRegex.containsMatchIn(value) -> repairName(value, slug)
        else -> value
    }
}

/** Public WetterOnline page with the map view, useful for verifying a slug. */
fun cityWebsiteUrl(slug: String): String = "$BASE_URL/wetter/$slug"
