package de.jayp33.sonnenregen.text

import de.jayp33.sonnenregen.domain.Change
import de.jayp33.sonnenregen.domain.Condition
import de.jayp33.sonnenregen.domain.Forecast
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

object ForecastFormatter {
    private val SUMMARY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm xx")

    /** One compact line for the notification: soonest event first. */
    fun compact(forecast: Forecast, phrases: ForecastPhrases): String =
        compact(forecast.sun, forecast.rain, phrases, forecast.rainOutlook.againInMinutes)

    fun compact(
        sun: Change,
        rain: Change,
        phrases: ForecastPhrases,
        rainAgainInMinutes: Long? = null,
    ): String {
        val first: Change
        val second: Change
        if (comesBefore(rain, sun)) {
            first = rain
            second = sun
        } else {
            first = sun
            second = rain
        }
        val firstText = phrase(
            first,
            phrases,
            rainAgainInMinutes.takeIf { first.condition == Condition.RAIN },
        )
        val secondText = phrase(
            second,
            phrases,
            rainAgainInMinutes.takeIf { second.condition == Condition.RAIN },
        ).let {
            if (phrases.lowerCaseSecond) it.replaceFirstChar { char -> char.lowercase() } else it
        }
        return format(phrases.pair, firstText, secondText)
    }

    /** Verbose single-condition text for the app UI. */
    fun verbose(change: Change, phrases: ForecastPhrases): String {
        val minutes = change.minutes ?: return when (change.condition) {
            Condition.SUN -> if (change.currently) phrases.summarySunOngoing else phrases.summaryNoSun
            Condition.RAIN -> if (change.currently) phrases.summaryRainOngoing else phrases.summaryNoRain
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
            }
        }
        if (change.condition != Condition.RAIN || minutes == null ||
            againInMinutes == null || againInMinutes <= 0
        ) {
            return text
        }
        return format(phrases.rainAgain, text, duration(againInMinutes, phrases))
    }

    private fun comesBefore(candidate: Change, other: Change): Boolean =
        orderKey(candidate) < orderKey(other)

    private fun orderKey(change: Change): Long = when {
        change.currently -> Long.MIN_VALUE
        else -> change.minutes ?: Long.MAX_VALUE
    }

    private fun format(template: String, vararg args: Any): String =
        String.format(Locale.getDefault(), template, *args)
}
