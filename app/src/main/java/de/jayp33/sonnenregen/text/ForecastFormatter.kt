package de.jayp33.sonnenregen.text

import de.jayp33.sonnenregen.domain.Change
import de.jayp33.sonnenregen.domain.Condition
import de.jayp33.sonnenregen.domain.Forecast
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

object ForecastFormatter {
    private val SUMMARY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm xx")

    /**
     * One compact line for the notification. Sun is always present. Rain and snow
     * each get a slot when they are forecast in the horizon, so both can appear
     * together. When neither is forecast, the plain "no rain" phrase is used so
     * the line never loses the sun information. Ties go sun > rain > snow.
     */
    fun compact(forecast: Forecast, phrases: ForecastPhrases): String {
        val rain = forecast.rain
        val hasRain = rain.currently || rain.minutes != null
        // Snow is optional: forecasts stored before snow support have none.
        val snow = forecast.snow?.takeIf { it.currently || it.minutes != null }
        val changes = buildList {
            add(forecast.sun)
            if (hasRain) add(rain)
            if (snow != null) add(snow)
            if (!hasRain && snow == null) add(rain) // renders as "no rain"
        }
        return compact(changes, phrases, forecast.rainOutlook.againInMinutes)
    }

    /** Two-condition form for callers without a whole forecast. */
    fun compact(
        sun: Change,
        rain: Change,
        phrases: ForecastPhrases,
        rainAgainInMinutes: Long? = null,
    ): String = compact(listOf(sun, rain), phrases, rainAgainInMinutes)

    private fun compact(
        changes: List<Change>,
        phrases: ForecastPhrases,
        rainAgainInMinutes: Long?,
    ): String {
        val ordered = changes.sortedWith(
            compareBy<Change> { orderKey(it) }.thenBy { tieBreak(it.condition) },
        )
        val texts = ordered.mapIndexed { index, change ->
            val text = phrase(
                change,
                phrases,
                rainAgainInMinutes.takeIf { change.condition == Condition.RAIN },
            )
            if (index > 0 && phrases.lowerCaseTrailing) {
                text.replaceFirstChar { it.lowercase() }
            } else {
                text
            }
        }
        val template = if (texts.size == 3) phrases.triple else phrases.pair
        return format(template, *texts.toTypedArray())
    }

    /** Verbose single-condition text for the app UI. */
    fun verbose(change: Change, phrases: ForecastPhrases): String {
        val minutes = change.minutes ?: return when (change.condition) {
            Condition.SUN -> if (change.currently) phrases.summarySunOngoing else phrases.summaryNoSun
            Condition.RAIN -> if (change.currently) phrases.summaryRainOngoing else phrases.summaryNoRain
            Condition.SNOW -> if (change.currently) phrases.summarySnowOngoing else phrases.summaryNoSnow
        }

        val duration = duration(minutes, phrases)
        val at = change.at?.format(SUMMARY_TIME).orEmpty()
        return when (change.condition) {
            Condition.SUN -> if (change.currently) {
                format(phrases.summarySunEnds, duration, at)
            } else {
                format(phrases.summarySunStarts, duration, at)
            }
            Condition.RAIN -> if (change.currently) {
                format(phrases.summaryRainEnds, duration, at)
            } else {
                format(phrases.summaryRainStarts, duration, at)
            }
            Condition.SNOW -> if (change.currently) {
                format(phrases.summarySnowEnds, duration, at)
            } else {
                format(phrases.summarySnowStarts, duration, at)
            }
        }
    }

    /** Verbose rain text, including a later shower inside the nowcast window. */
    fun verboseRain(forecast: Forecast, phrases: ForecastPhrases): String {
        val base = verbose(forecast.rain, phrases)
        val again = forecast.rainOutlook.againInMinutes ?: return base
        if (forecast.rain.minutes == null || again <= 0) return base
        return format(phrases.summaryRainAgain, base, duration(again, phrases))
    }

    fun duration(minutes: Long, phrases: ForecastPhrases): String {
        val sign = if (minutes < 0) "-" else ""
        val absolute = abs(minutes)
        val hours = absolute / 60
        val rest = absolute % 60
        val value = when {
            hours > 0 && rest > 0 -> format(phrases.durationHoursMinutes, hours, rest)
            hours > 0 -> format(phrases.durationHours, hours)
            else -> format(phrases.durationMinutes, rest)
        }
        return sign + value
    }

    private fun phrase(
        change: Change,
        phrases: ForecastPhrases,
        againInMinutes: Long? = null,
    ): String {
        val minutes = change.minutes
        val text = if (minutes == null) {
            when (change.condition) {
                Condition.SUN -> if (change.currently) phrases.sunNow else phrases.noSun
                Condition.RAIN -> if (change.currently) phrases.rainNow else phrases.noRain
                Condition.SNOW -> if (change.currently) phrases.snowNow else phrases.noSnow
            }
        } else {
            val duration = duration(minutes, phrases)
            when (change.condition) {
                Condition.SUN -> if (change.currently) {
                    format(phrases.sunFor, duration)
                } else {
                    format(phrases.sunIn, duration)
                }
                Condition.RAIN -> if (change.currently) {
                    format(phrases.rainFor, duration)
                } else {
                    format(phrases.rainIn, duration)
                }
                Condition.SNOW -> if (change.currently) {
                    format(phrases.snowFor, duration)
                } else {
                    format(phrases.snowIn, duration)
                }
            }
        }
        if (change.condition != Condition.RAIN || minutes == null ||
            againInMinutes == null || againInMinutes <= 0
        ) {
            return text
        }
        return format(phrases.rainAgain, text, duration(againInMinutes, phrases))
    }

    /** On equal onset times, keep the old sun-first rule and let rain beat snow. */
    private fun tieBreak(condition: Condition): Int = when (condition) {
        Condition.SUN -> 0
        Condition.RAIN -> 1
        Condition.SNOW -> 2
    }

    private fun orderKey(change: Change): Long = when {
        change.currently -> Long.MIN_VALUE
        else -> change.minutes ?: Long.MAX_VALUE
    }

    private fun format(template: String, vararg args: Any): String =
        String.format(Locale.getDefault(), template, *args)
}
