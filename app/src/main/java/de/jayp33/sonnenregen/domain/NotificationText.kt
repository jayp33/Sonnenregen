package de.jayp33.sonnenregen.domain

object NotificationText {
    /** One compact line for the notification: soonest event first. */
    fun compact(forecast: Forecast): String = compact(forecast.sun, forecast.rain)

    fun compact(sun: Change, rain: Change): String {
        val first: Change
        val second: Change
        if (comesBefore(rain, sun)) {
            first = rain
            second = sun
        } else {
            first = sun
            second = rain
        }
        return "${phrase(first.label, first)}, ${lowerFirst(phrase(second.label, second))}"
    }

    private fun comesBefore(candidate: Change, other: Change): Boolean =
        orderKey(candidate) < orderKey(other)

    private fun orderKey(change: Change): Long = when {
        change.currently -> Long.MIN_VALUE
        else -> change.minutes ?: Long.MAX_VALUE
    }

    private fun phrase(label: String, change: Change): String {
        val shortLabel = shortLabel(label)
        val minutes = change.minutes
        if (minutes == null) {
            return if (change.currently) "$shortLabel now" else "no ${shortLabel.lowercase()}"
        }
        return if (change.currently) {
            "$shortLabel for ${formatMinutes(minutes)}"
        } else {
            "$shortLabel in ${formatMinutes(minutes)}"
        }
    }

    private fun shortLabel(label: String): String = when {
        label.startsWith("Sun", ignoreCase = true) -> "Sun"
        label.startsWith("Rain", ignoreCase = true) -> "Rain"
        else -> label
    }

    private fun lowerFirst(value: String): String = value.replaceFirstChar { it.lowercase() }
}

fun formatMinutes(minutes: Long): String {
    val sign = if (minutes < 0) "-" else ""
    val absolute = kotlin.math.abs(minutes)
    val hours = absolute / 60
    val rest = absolute % 60
    return when {
        hours > 0 && rest > 0 -> "$sign$hours h $rest min"
        hours > 0 -> "$sign$hours h"
        else -> "$sign$rest min"
    }
}
