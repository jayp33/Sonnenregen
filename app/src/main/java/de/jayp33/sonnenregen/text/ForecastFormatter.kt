package de.jayp33.sonnenregen.text

import de.jayp33.sonnenregen.domain.Change
import de.jayp33.sonnenregen.domain.Condition
import de.jayp33.sonnenregen.domain.Event
import de.jayp33.sonnenregen.domain.Forecast
import java.time.Duration
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

object ForecastFormatter {
    private val SUMMARY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm xx")

    /**
     * One compact line for the notification. Sun is always present. Rain and snow
     * each get a slot when they are forecast in the horizon, so both can appear
     * together. When neither is forecast, the plain "no rain" phrase is used so
     * the line never loses the sun information. Ties go sun > rain > snow.
     *
     * With [now], remaining times are rebased on the current clock instead of the
     * forecast's own observation time.
     */
    fun compact(forecast: Forecast, phrases: ForecastPhrases, now: OffsetDateTime? = null): String {
        val rain = forecast.rain
        val hasRain = rain.currently || rain.minutes != null
        // Snow is optional: forecasts stored before snow support have none.
        val snow = forecast.snow?.takeIf { it.currently || it.minutes != null }
        // Snow ending exactly when rain begins (or vice versa) is one handoff,
        // not two separate onsets: "Snow for 1 h, then rain, no sun".
        val handoffTo = when {
            snow != null && followsDirectly(snow, rain) -> Condition.RAIN
            snow != null && followsDirectly(rain, snow) -> Condition.SNOW
            else -> null
        }
        val changes = buildList {
            add(forecast.sun)
            if (hasRain) add(rain)
            if (snow != null) add(snow)
            if (!hasRain && snow == null) add(rain) // renders as "no rain"
        }
        val again = forecast.rainOutlook.againInMinutes?.let { minutes ->
            if (now == null) minutes else minutesBetween(now, forecast.now.plusMinutes(minutes))
        }
        return compact(changes, phrases, again, handoffTo, now)
    }

    /** Two-condition form for callers without a whole forecast. */
    fun compact(
        sun: Change,
        rain: Change,
        phrases: ForecastPhrases,
        rainAgainInMinutes: Long? = null,
        now: OffsetDateTime? = null,
    ): String = compact(listOf(sun, rain), phrases, rainAgainInMinutes, null, now)

    /**
     * Compact line from an explicit change list. Stored forecasts use this after
     * restoring their condition changes, so the line can age without a refresh.
     */
    fun compact(
        changes: List<Change>,
        phrases: ForecastPhrases,
        rainAgainInMinutes: Long? = null,
        handoffTo: Condition? = null,
        now: OffsetDateTime? = null,
    ): String {
        val rendered = if (now == null) changes else changes.map { aged(it, now) }
        val ordered = rendered.sortedWith(
            compareBy<Change> { orderKey(it) }.thenBy { tieBreak(it.condition) },
        )
        val texts = ordered.mapIndexed { index, change ->
            val text = when {
                // Once the handoff time has passed the event is terminal; do not
                // keep calling it "then rain" after rain has actually begun.
                change.condition == handoffTo && change.event != null &&
                    change.condition == Condition.RAIN -> phrases.thenRain
                change.condition == handoffTo && change.event != null &&
                    change.condition == Condition.SNOW -> phrases.thenSnow
                else -> phrase(
                    change,
                    phrases,
                    rainAgainInMinutes.takeIf { change.condition == Condition.RAIN },
                )
            }
            if (index > 0 && phrases.lowerCaseTrailing) {
                text.replaceFirstChar { it.lowercase() }
            } else {
                text
            }
        }
        val template = if (texts.size == 3) phrases.triple else phrases.pair
        return format(template, *texts.toTypedArray())
    }

    /**
     * Rebase a change on [now]. A still-future event keeps its absolute time and
     * gets a new remaining duration. Once it has passed, its opposite state is
     * known but no following event is stored, so the line ends there.
     */
    fun aged(change: Change, now: OffsetDateTime): Change {
        val at = change.at ?: return change
        val minutes = minutesBetween(now, at)
        if (minutes > 0L) return change.copy(minutes = minutes)
        return Change(
            currently = change.event == Event.STARTS,
            event = null,
            at = null,
            minutes = null,
            condition = change.condition,
        )
    }

    /** Verbose single-condition text for the app UI. */
    fun verbose(change: Change, phrases: ForecastPhrases, now: OffsetDateTime? = null): String {
        val current = if (now == null) change else aged(change, now)
        val minutes = current.minutes ?: return when (current.condition) {
            Condition.SUN -> if (current.currently) phrases.summarySunOngoing else phrases.summaryNoSun
            Condition.RAIN -> if (current.currently) phrases.summaryRainOngoing else phrases.summaryNoRain
            Condition.SNOW -> if (current.currently) phrases.summarySnowOngoing else phrases.summaryNoSnow
        }

        val duration = duration(minutes, phrases)
        val at = current.at?.format(SUMMARY_TIME).orEmpty()
        return when (current.condition) {
            Condition.SUN -> if (current.currently) {
                format(phrases.summarySunEnds, duration, at)
            } else {
                format(phrases.summarySunStarts, duration, at)
            }
            Condition.RAIN -> if (current.currently) {
                format(phrases.summaryRainEnds, duration, at)
            } else {
                format(phrases.summaryRainStarts, duration, at)
            }
            Condition.SNOW -> if (current.currently) {
                format(phrases.summarySnowEnds, duration, at)
            } else {
                format(phrases.summarySnowStarts, duration, at)
            }
        }
    }

    /** Verbose rain text, including a later shower inside the nowcast window. */
    fun verboseRain(forecast: Forecast, phrases: ForecastPhrases, now: OffsetDateTime? = null): String {
        val again = forecast.rainOutlook.againInMinutes?.let { minutes ->
            if (now == null) minutes else minutesBetween(now, forecast.now.plusMinutes(minutes))
        }
        return verboseRain(forecast.rain, phrases, again, now)
    }

    /** Verbose rain text from an explicit change and a possibly aged onset. */
    fun verboseRain(
        change: Change,
        phrases: ForecastPhrases,
        againInMinutes: Long? = null,
        now: OffsetDateTime? = null,
    ): String {
        val base = verbose(change, phrases, now)
        val again = againInMinutes ?: return base
        val current = if (now == null) change else aged(change, now)
        if (current.minutes == null || again <= 0) return base
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

    /** True when [ending]'s end time is exactly [starting]'s onset. */
    private fun followsDirectly(ending: Change, starting: Change): Boolean {
        val end = ending.at ?: return false
        return ending.currently && !starting.currently &&
            ending.event == Event.ENDS && starting.event == Event.STARTS &&
            end.toInstant() == starting.at?.toInstant()
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

    private fun minutesBetween(from: OffsetDateTime, to: OffsetDateTime): Long =
        Duration.between(from, to).seconds.toDouble().div(60).roundToLong()

    private fun format(template: String, vararg args: Any): String =
        String.format(Locale.getDefault(), template, *args)
}
