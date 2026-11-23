package de.jayp33.sonnenregen.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

class ParseException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class PageData(
    val shortcast: ShortcastDto,
    val astro: AstroDto?,
    /** Human-readable place name from the page, e.g. "Frankfurt (Oder)". */
    val placeName: String? = null,
    /** Canonical slug from the page's own links, which resolves aliases like "waren". */
    val canonicalSlug: String? = null,
)

object ForecastParser {
    private const val SHORTCAST_URL = "https://api-web.wo-cloud.com/blending/shortcast/v1?"
    private const val ASTRO_URL = "https://api-web.wo-cloud.com/astro/days/v1?"

    private val ngStateRegex = Regex(
        """(?is)<script[^>]*\bid=["']ng-state["'][^>]*>(.*?)</script>""",
    )
    private val h1Regex = Regex("""(?is)<h1[^>]*>(.*?)</h1>""")
    private val titleRegex = Regex("""(?is)<title[^>]*>(.*?)</title>""")
    private val canonicalSlugRegex = Regex(
        """/(?:wetterdaten|wetterradar)/([a-z0-9]+(?:-[a-z0-9]+)*(?:/[a-z0-9]+(?:-[a-z0-9]+)*)*)""",
    )
    private val tagRegex = Regex("""<[^>]+>""")
    private val entityRegex = Regex(
        """&(#(?:x[0-9a-f]+|[0-9]+)|[a-z]+);""",
        RegexOption.IGNORE_CASE,
    )
    private val weatherPrefixRegex = Regex(
        """^(?:14-Tage-Wetter|14-Day Weather|Wetter|Weather)\s+""",
        RegexOption.IGNORE_CASE,
    )
    private val siteSuffixRegex = Regex(
        """\s*-\s*WetterOnline\s*$""",
        RegexOption.IGNORE_CASE,
    )
    private val genericNameRegex = Regex(
        """^(?:wetter|startseite|navigation|menü|suche|login|home|wettertrend|90[ -]minuten[ -]wetter)$""",
        RegexOption.IGNORE_CASE,
    )
    /**
     * Some page variants append a descriptor after the place, e.g.
     * "Frankfurt (Oder) - 4 Tage Vorhersage". Only drop such a suffix when it
     * looks like page boilerplate; a hyphen inside a real name has no spaces.
     */
    private val descriptorSuffixRegex = Regex(
        """\s+-\s+(?=\d|.*(?:Vorhersage|Prognose|Wetter|Tage)).*$""",
        RegexOption.IGNORE_CASE,
    )
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * WetterOnline puts the real place name in the heading: "Wetter Berlin",
     * "Wetter Frankfurt (Oder)", "Gschlachtenbretzingen" on the nowcast page.
     * The title is a fallback; neither is part of the machine-readable payload.
     */
    internal fun extractPlaceName(html: String): String? {
        val fromHeading = h1Regex.findAll(html)
            .mapNotNull { match -> cleanName(match.groupValues[1]).takeIf(::isPlaceName) }
            .firstOrNull()
        if (fromHeading != null) return fromHeading

        return titleRegex.findAll(html)
            .mapNotNull { match -> cleanName(match.groupValues[1]).takeIf(::isPlaceName) }
            .firstOrNull()
    }

    private fun isPlaceName(name: String): Boolean =
        name.isNotBlank() &&
            !name.contains("WetterOnline", ignoreCase = true) &&
            !genericNameRegex.matches(name)

    private fun cleanName(raw: String): String = tagRegex.replace(raw, " ")
        .let(::decodeEntities)
        .replace(Regex("""\s+"""), " ")
        .trim()
        .let { weatherPrefixRegex.replace(it, "") }
        .let { siteSuffixRegex.replace(it, "") }
        .let { descriptorSuffixRegex.replace(it, "") }
        .trim()
        .takeIf { it.length in 1..120 } ?: ""

    private val namedEntities = mapOf(
        "nbsp" to " ",
        "amp" to "&",
        "quot" to "\"",
        "apos" to "'",
        "lt" to "<",
        "gt" to ">",
        "auml" to "ä",
        "Auml" to "Ä",
        "ouml" to "ö",
        "Ouml" to "Ö",
        "uuml" to "ü",
        "Uuml" to "Ü",
        "szlig" to "ß",
    )

    private fun decodeEntities(value: String): String = entityRegex.replace(value) { match ->
        val entity = match.groupValues[1]
        when {
            entity.startsWith("#") -> decodeNumericEntity(entity, match.value)
            else -> namedEntities[entity] ?: match.value
        }
    }

    private fun decodeNumericEntity(entity: String, fallback: String): String {
        val code = if (entity.startsWith("#x", ignoreCase = true)) {
            entity.substring(2).toIntOrNull(16)
        } else {
            entity.substring(1).toIntOrNull()
        }
        return code?.let { value -> runCatching { Char(value).toString() }.getOrNull() }
            ?: fallback
    }

    fun parsePage(html: String): ShortcastDto = parsePageData(html).shortcast

    fun parsePageData(html: String): PageData {
        val match = ngStateRegex.find(html)
            ?: throw ParseException("No ng-state payload found in the page")

        val state = json.parseToJsonElement(match.groupValues[1]) as? JsonObject
            ?: throw ParseException("Unexpected ng-state payload")

        val shortcastValue = state.usableEntry(SHORTCAST_URL, "hours")
            ?: throw ParseException("No shortcast data found in the ng-state payload")
        val shortcast = try {
            json.decodeFromJsonElement(ShortcastDto.serializer(), shortcastValue)
        } catch (exc: Exception) {
            throw ParseException("Invalid shortcast payload: ${exc.message}", exc)
        }

        // Sunrise/sunset is a bonus: a missing or malformed astro payload must
        // not make an otherwise usable forecast fail.
        val astro = state.usableEntry(ASTRO_URL, "days")?.let { value ->
            runCatching { json.decodeFromJsonElement(AstroDto.serializer(), value) }.getOrNull()
        }

        return PageData(shortcast, astro, extractPlaceName(html), canonicalSlug(html))
    }

    /** Prefer the first entry that has the expected shape, not just a matching URL. */
    private fun JsonObject.usableEntry(urlPrefix: String, field: String): JsonElement? =
        entries.firstOrNull { (key, value) ->
            key.startsWith(urlPrefix) && value is JsonObject && value[field] is JsonArray
        }?.value

    private fun canonicalSlug(html: String): String? = canonicalSlugRegex.findAll(html)
        .map { it.groupValues[1] }
        .groupingBy { it }
        .eachCount()
        .maxByOrNull { it.value }
        ?.key
}

@Serializable
data class ShortcastDto(
    val hours: List<HourDto> = emptyList(),
    @SerialName("nowcast_trend") val nowcastTrend: NowcastDto? = null,
    val current: CurrentDto? = null,
)

@Serializable
data class HourDto(
    val date: String,
    val symbol: String,
)

@Serializable
data class NowcastDto(
    val items: List<ItemDto> = emptyList(),
)

@Serializable
data class ItemDto(
    val date: String,
    val symbol: String,
    @SerialName("weather_condition_image") val weatherConditionImage: String? = null,
)

@Serializable
data class CurrentDto(
    val date: String,
    val symbol: String,
    @SerialName("weather_condition_image") val weatherConditionImage: String? = null,
    @SerialName("solar_elevation") val solarElevation: Double? = null,
)

@Serializable
data class AstroDto(
    val days: List<AstroDayDto> = emptyList(),
)

@Serializable
data class AstroDayDto(
    val date: String,
    val sun: AstroSunDto? = null,
)

@Serializable
data class AstroSunDto(
    @SerialName("course_type") val courseType: String? = null,
    val rise: String? = null,
    val set: String? = null,
)
