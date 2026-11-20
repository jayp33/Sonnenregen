package de.jayp33.sonnenregen.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

class ParseException(message: String, cause: Throwable? = null) : Exception(message, cause)

object ForecastParser {
    private val ngStateRegex = Regex(
        """(?is)<script[^>]*\bid=["']ng-state["'][^>]*>(.*?)</script>""",
    )
    private val json = Json { ignoreUnknownKeys = true }

    fun parsePage(html: String): ShortcastDto {
        val match = ngStateRegex.find(html)
            ?: throw ParseException("No ng-state payload found in the page")

        val state = json.parseToJsonElement(match.groupValues[1]) as? JsonObject
            ?: throw ParseException("Unexpected ng-state payload")

        val shortcast = state.entries.firstOrNull { it.key.contains("/blending/shortcast/v1?") }
            ?: throw ParseException("No shortcast data found in the ng-state payload")

        return try {
            json.decodeFromJsonElement(ShortcastDto.serializer(), shortcast.value)
        } catch (exc: Exception) {
            throw ParseException("Invalid shortcast payload: ${exc.message}", exc)
        }
    }
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
