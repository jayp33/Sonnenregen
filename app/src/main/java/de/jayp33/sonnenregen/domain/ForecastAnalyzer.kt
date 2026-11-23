package de.jayp33.sonnenregen.domain

import de.jayp33.sonnenregen.data.CurrentDto
import de.jayp33.sonnenregen.data.ForecastParser
import de.jayp33.sonnenregen.data.HourDto
import de.jayp33.sonnenregen.data.ItemDto
import de.jayp33.sonnenregen.data.ParseException
import de.jayp33.sonnenregen.data.ShortcastDto
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlin.math.roundToLong

const val BASE_URL = "https://www.wetteronline.de"
const val TIMEZONE = "Europe/Berlin"

object ForecastAnalyzer {
    fun analyze(
        city: String,
        hourlyHtml: String,
        nowcastHtml: String,
        now: OffsetDateTime? = null,
    ): Forecast {
        val normalizedCity = normalizeCity(city)
        require(isValidCitySlug(normalizedCity)) { "Invalid city slug: $normalizedCity" }

        val hourlyPage = ForecastParser.parsePageData(hourlyHtml)
        val nowcastPage = ForecastParser.parsePageData(nowcastHtml)
        val hourlyShortcast = hourlyPage.shortcast
        val nowcastShortcast = nowcastPage.shortcast
        // Aliases such as "waren" redirect to "waren-mueritz"; the page links
        // carry the canonical spelling needed to repair its mangled umlaut.
        val displaySlug = hourlyPage.canonicalSlug ?: normalizedCity

        val (hours, nearcast, hourlyCurrent) = slotsFrom(hourlyShortcast)
        val (_, nowcastItems, nowcastCurrent) = slotsFrom(nowcastShortcast)

        val nowcast = nowcastItems.ifEmpty { nearcast }
        val nowcastHorizon = nowcast.maxOfOrNull { it.end }
        val current = hourlyCurrent ?: nowcastCurrent
        if (hours.isEmpty()) throw ParseException("The hourly forecast is empty")

        val reference = now ?: current?.start ?: OffsetDateTime.now(ZoneId.of(TIMEZONE))
        val timeline = buildTimeline(reference, hours, nowcast, current)
        val rainOutlook = RainOutlook(
            againInMinutes = nowcastHorizon?.let { horizon ->
                conditionWindows(reference, timeline, { it.raining }, horizon)
                    .drop(1)
                    .firstOrNull()
                    ?.let { minutesBetween(reference, it.start) }
                    ?.takeIf { it > 0 }
            },
        )
        val windows = daylightWindows(hourlyPage.astro)
        val sunTimeline = applyDaylight(timeline, windows)

        return Forecast(
            city = normalizedCity,
            displayName = displayNameFor(displaySlug, hourlyPage.placeName),
            now = reference,
            timezone = hours.firstOrNull()?.start?.offset?.id ?: TIMEZONE,
            sun = change(reference, sunTimeline, { it.sunny }, Condition.SUN),
            rain = change(reference, timeline, { it.raining }, Condition.RAIN),
            rainOutlook = rainOutlook,
            horizon = timeline.last().end,
            sources = listOf(
                "$BASE_URL/wettertrend/$normalizedCity",
                "$BASE_URL/90-minuten-wetter/$normalizedCity",
            ),
            sunrise = windows.map { it.rise }.firstOrNull { !it.isBefore(reference) },
            sunset = windows.map { it.set }.firstOrNull { !it.isBefore(reference) },
            hours = hourlyCells(reference, hours, current, windows),
            nowcastCells = nowcastCells(reference, nowcast),
            sunEvents = sunEventsFrom(windows),
        )
    }

    fun slotsFrom(dto: ShortcastDto): Triple<List<Slot>, List<Slot>, Slot?> {
        val hours = dto.hours.mapNotNull { hour ->
            Slot(
                start = parseTimestamp(hour.date),
                duration = Duration.ofMinutes(60),
                symbol = hour.symbol,
                source = SlotSource.HOURLY,
            )
        }.sortedBy { it.start }

        val nowcast = dto.nowcastTrend?.items.orEmpty().mapNotNull { item ->
            Slot(
                start = parseTimestamp(item.date),
                duration = Duration.ofMinutes(15),
                symbol = item.symbol,
                source = SlotSource.NOWCAST,
                night = item.weatherConditionImage == "night",
            )
        }.sortedBy { it.start }

        val current = dto.current?.let { it ->
            Slot(
                start = parseTimestamp(it.date),
                duration = Duration.ofMinutes(15),
                symbol = it.symbol,
                source = SlotSource.CURRENT,
                solarElevation = it.solarElevation,
                night = it.weatherConditionImage == "night",
            )
        }

        return Triple(hours, nowcast, current)
    }

    fun buildTimeline(
        now: OffsetDateTime,
        hourly: List<Slot>,
        nowcast: List<Slot>,
        current: Slot?,
    ): List<Slot> {
        val timeline = mutableListOf<Slot>()

        if (nowcast.isNotEmpty()) {
            val lastNowcast = nowcast.maxOf { it.start }
            val followingHourly = hourly.firstOrNull { it.start > lastNowcast }
            timeline.addAll(nowcast.filter { it.end.isAfter(now) })

            if (timeline.isEmpty()) {
                timeline.addAll(hourly.filter { it.end.isAfter(now) })
            } else if (followingHourly != null) {
                val gap = Duration.between(timeline.last().start, followingHourly.start)
                if (gap > Duration.ZERO) {
                    timeline[timeline.lastIndex] = timeline.last().copy(duration = gap)
                }
                timeline.addAll(hourly.filter { !it.start.isBefore(followingHourly.start) })
            } else {
                timeline.addAll(hourly.filter { it.start.isAfter(lastNowcast) })
            }
        } else {
            timeline.addAll(hourly.filter { it.end.isAfter(now) })
        }

        if (current != null && (timeline.isEmpty() || current.start.isBefore(timeline.first().start))) {
            val duration = if (timeline.isEmpty()) {
                Duration.ofHours(1)
            } else {
                Duration.between(current.start, timeline.first().start)
            }
            if (duration > Duration.ZERO) timeline.add(0, current.copy(duration = duration))
        } else if (current != null && timeline.isNotEmpty() && current.start.isBefore(timeline.first().end)) {
            val first = timeline.first()
            timeline[0] = current.copy(start = first.start, duration = first.duration)
        }

        val cleaned = mutableListOf<Slot>()
        for (slot in timeline.sortedBy { it.start }) {
            var adjusted = slot
            if (adjusted.duration <= Duration.ZERO) continue
            if (cleaned.isNotEmpty() && adjusted.start.isBefore(cleaned.last().end)) {
                val previousEnd = cleaned.last().end
                val remaining = Duration.between(previousEnd, adjusted.end)
                if (remaining <= Duration.ZERO) continue
                adjusted = adjusted.copy(start = previousEnd, duration = remaining)
            }
            cleaned.add(adjusted)
        }

        if (cleaned.isEmpty()) throw ParseException("No forecast slots available")
        if (now.isBefore(cleaned.first().start)) {
            val gap = Duration.between(now, cleaned.first().start)
            if (gap > Duration.ZERO) {
                cleaned.add(
                    0,
                    Slot(now, gap, cleaned.first().symbol, SlotSource.INTERPOLATED),
                )
            }
        }
        return cleaned
    }

    /** Continuous spans where [condition] holds, clipped to [until] and `now`. */
    private fun conditionWindows(
        now: OffsetDateTime,
        timeline: List<Slot>,
        condition: (Slot) -> Boolean,
        until: OffsetDateTime,
    ): List<TimeWindow> {
        val result = mutableListOf<TimeWindow>()
        var start: OffsetDateTime? = null
        for (slot in timeline) {
            val end = minOf(slot.end, until)
            if (!end.isAfter(now) || !slot.start.isBefore(until)) continue
            if (condition(slot)) {
                if (start == null) start = maxOf(slot.start, now)
            } else if (start != null) {
                val close = maxOf(slot.start, start)
                if (close.isAfter(start)) result += TimeWindow(start, close)
                start = null
            }
        }
        if (start != null && until.isAfter(start)) result += TimeWindow(start, until)
        return result
    }

    private fun minutesBetween(from: OffsetDateTime, to: OffsetDateTime): Long =
        Duration.between(from, to).seconds.toDouble().div(60).roundToLong()

    private fun change(
        now: OffsetDateTime,
        timeline: List<Slot>,
        condition: (Slot) -> Boolean,
        kind: Condition,
    ): Change {
        val index = currentIndex(now, timeline)
        val currently = condition(timeline[index])
        val event = if (currently) Event.ENDS else Event.STARTS
        val wanted = !currently

        val target = timeline.drop(index + 1).firstOrNull { condition(it) == wanted }
            ?: return Change(
                currently = currently,
                event = null,
                at = null,
                minutes = null,
                condition = kind,
            )

        val minutes = minutesBetween(now, target.start)
        return Change(currently, event, target.start, minutes, kind)
    }

    private fun currentIndex(now: OffsetDateTime, timeline: List<Slot>): Int {
        timeline.forEachIndexed { index, slot ->
            if (!slot.start.isAfter(now) && slot.end.isAfter(now)) return index
        }
        return 0
    }
}
