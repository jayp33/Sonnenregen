package de.jayp33.sonnenregen.text

enum class AgeUnit { JUST_NOW, MINUTES, HOURS, DAYS }

data class RelativeAge(val unit: AgeUnit, val value: Long)

/**
 * Coarse age buckets for "updated x ago" labels. Missing timestamps have no age,
 * and future timestamps (clock skew) are treated as just now.
 */
fun relativeAge(fetchedAtMillis: Long, nowMillis: Long): RelativeAge? {
    if (fetchedAtMillis <= 0L) return null

    val minutes = (nowMillis - fetchedAtMillis).coerceAtLeast(0L) / 60_000L
    return when {
        minutes < 1L -> RelativeAge(AgeUnit.JUST_NOW, 0L)
        minutes < 60L -> RelativeAge(AgeUnit.MINUTES, minutes)
        minutes < 24L * 60L -> RelativeAge(AgeUnit.HOURS, minutes / 60L)
        else -> RelativeAge(AgeUnit.DAYS, minutes / (24L * 60L))
    }
}
