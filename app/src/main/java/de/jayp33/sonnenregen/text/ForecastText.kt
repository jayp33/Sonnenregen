package de.jayp33.sonnenregen.text

import de.jayp33.sonnenregen.data.CityForecast
import de.jayp33.sonnenregen.domain.ChangeSnapshot
import de.jayp33.sonnenregen.domain.Condition
import de.jayp33.sonnenregen.domain.Event
import de.jayp33.sonnenregen.domain.toChange
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Renders stored forecasts at the current clock time. Forecasts saved before
 * structured changes existed keep their original strings as a fallback.
 */
object ForecastText {
    fun compact(forecast: CityForecast, nowMillis: Long, phrases: ForecastPhrases): String {
        if (!canAge(forecast, nowMillis)) return forecast.compactSummary
        val sun = forecast.sunChange ?: return forecast.compactSummary
        val rain = forecast.rainChange ?: return forecast.compactSummary
        val snow = forecast.snowChange
        val zone = zone(forecast)
        val hasRain = rain.currently || rain.event != null
        val hasSnow = snow != null && (snow.currently || snow.event != null)

        val changes = buildList {
            add(sun.toChange(zone))
            if (hasRain) add(rain.toChange(zone))
            if (snow != null && hasSnow) add(snow.toChange(zone))
            if (!hasRain && !hasSnow) add(rain.toChange(zone)) // renders as "no rain"
        }
        return ForecastFormatter.compact(
            changes = changes,
            phrases = phrases,
            rainAgainInMinutes = againInMinutes(forecast, nowMillis),
            handoffTo = handoff(snow, rain),
            now = at(forecast, nowMillis, zone),
        )
    }

    fun sunSummary(forecast: CityForecast, nowMillis: Long, phrases: ForecastPhrases): String {
        if (!canAge(forecast, nowMillis)) return forecast.sunSummary
        val change = forecast.sunChange ?: return forecast.sunSummary
        val zone = zone(forecast)
        return ForecastFormatter.verbose(change.toChange(zone), phrases, at(forecast, nowMillis, zone))
    }

    fun rainSummary(forecast: CityForecast, nowMillis: Long, phrases: ForecastPhrases): String {
        if (!canAge(forecast, nowMillis)) return forecast.rainSummary
        val change = forecast.rainChange ?: return forecast.rainSummary
        val zone = zone(forecast)
        return ForecastFormatter.verboseRain(
            change = change.toChange(zone),
            phrases = phrases,
            againInMinutes = againInMinutes(forecast, nowMillis),
            now = at(forecast, nowMillis, zone),
        )
    }

    fun snowSummary(forecast: CityForecast, nowMillis: Long, phrases: ForecastPhrases): String {
        if (!canAge(forecast, nowMillis)) return forecast.snowSummary
        val change = forecast.snowChange?.takeIf { it.currently || it.event != null }
            ?: return forecast.snowSummary
        val zone = zone(forecast)
        return ForecastFormatter.verbose(change.toChange(zone), phrases, at(forecast, nowMillis, zone))
    }

    /** A direct handoff is one phrase, not two repeated times. */
    private fun handoff(snow: ChangeSnapshot?, rain: ChangeSnapshot): Condition? = when {
        snow != null && snow.currently && !rain.currently &&
            snow.event == Event.ENDS && rain.event == Event.STARTS &&
            snow.atEpochMillis == rain.atEpochMillis -> Condition.RAIN

        snow != null && rain.currently && !snow.currently &&
            rain.event == Event.ENDS && snow.event == Event.STARTS &&
            rain.atEpochMillis == snow.atEpochMillis -> Condition.SNOW

        else -> null
    }

    /** A clock moved backwards must not inflate stored countdowns. */
    private fun canAge(forecast: CityForecast, nowMillis: Long): Boolean =
        nowMillis >= forecast.referenceMillis

    private fun againInMinutes(forecast: CityForecast, nowMillis: Long): Long? =
        forecast.rainAgainAtMillis?.let { (it - nowMillis + 30_000L) / 60_000L }

    private fun at(forecast: CityForecast, nowMillis: Long, zone: ZoneId): OffsetDateTime =
        Instant.ofEpochMilli(nowMillis).atZone(zone).toOffsetDateTime()

    private fun zone(forecast: CityForecast): ZoneId =
        runCatching { ZoneId.of(forecast.timezone) }.getOrDefault(ZoneOffset.UTC)
}
