package de.jayp33.sonnenregen.text

import android.content.Context
import de.jayp33.sonnenregen.R

data class ForecastPhrases(
    val lowerCaseSecond: Boolean,
    val pair: String,
    val durationHoursMinutes: String,
    val durationHours: String,
    val durationMinutes: String,
    val sunIn: String,
    val sunFor: String,
    val sunNow: String,
    val rainIn: String,
    val rainFor: String,
    val rainNow: String,
    val rainAgain: String,
    val noSun: String,
    val noRain: String,
    val summarySunStarts: String,
    val summarySunEnds: String,
    val summaryRainStarts: String,
    val summaryRainEnds: String,
    val summaryRainAgain: String,
    val summarySunOngoing: String,
    val summaryRainOngoing: String,
    val summaryNoSun: String,
    val summaryNoRain: String,
) {
    companion object {
        fun from(context: Context) = ForecastPhrases(
            lowerCaseSecond = context.resources.getBoolean(R.bool.forecast_lowercase_second),
            pair = context.getString(R.string.forecast_pair),
            durationHoursMinutes = context.getString(R.string.duration_hours_minutes),
            durationHours = context.getString(R.string.duration_hours),
            durationMinutes = context.getString(R.string.duration_minutes),
            sunIn = context.getString(R.string.sun_in),
            sunFor = context.getString(R.string.sun_for),
            sunNow = context.getString(R.string.sun_now),
            rainIn = context.getString(R.string.rain_in),
            rainFor = context.getString(R.string.rain_for),
            rainNow = context.getString(R.string.rain_now),
            rainAgain = context.getString(R.string.rain_again),
            noSun = context.getString(R.string.no_sun),
            noRain = context.getString(R.string.no_rain),
            summarySunStarts = context.getString(R.string.summary_sun_starts),
            summarySunEnds = context.getString(R.string.summary_sun_ends),
            summaryRainStarts = context.getString(R.string.summary_rain_starts),
            summaryRainEnds = context.getString(R.string.summary_rain_ends),
            summaryRainAgain = context.getString(R.string.summary_rain_again),
            summarySunOngoing = context.getString(R.string.summary_sun_ongoing),
            summaryRainOngoing = context.getString(R.string.summary_rain_ongoing),
            summaryNoSun = context.getString(R.string.summary_no_sun),
            summaryNoRain = context.getString(R.string.summary_no_rain),
        )
    }
}
