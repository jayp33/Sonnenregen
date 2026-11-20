package de.jayp33.sonnenregen.domain

const val DEFAULT_CITY = "berlin"

private val CITY_REGEX = Regex("^[a-z0-9]+(?:-[a-z0-9]+)*$")

/** Accept user input such as " Berlin " and return the WetterOnline slug. */
fun normalizeCity(input: String): String = input.trim().lowercase()

/** Guard URL construction against unexpected characters. */
fun isValidCitySlug(value: String): Boolean = CITY_REGEX.matches(value)

/** Slugs stay lowercase for URLs; show a human-friendly name instead. */
fun displayCity(slug: String): String = slug.replaceFirstChar { it.uppercase() }
