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
