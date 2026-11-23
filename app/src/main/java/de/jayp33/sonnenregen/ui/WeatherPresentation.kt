package de.jayp33.sonnenregen.ui

import androidx.compose.ui.graphics.Color
import de.jayp33.sonnenregen.R
import de.jayp33.sonnenregen.domain.HourKind

/** Shared by the in-app grid and the home-screen widget so colours never drift. */
internal fun HourKind.color(): Color = when (this) {
    HourKind.SUN -> Color(0xFFF2C230)
    HourKind.PARTLY_SUN -> Color(0xFFF7DE8C)
    HourKind.CLOUDY,
    HourKind.OVERCAST,
    -> Color(0xFFE0E0E0)
    HourKind.LIGHT_RAIN -> Color(0xFFA9D2F2)
    HourKind.RAIN -> Color(0xFF4C9BD6)
    HourKind.HEAVY_RAIN -> Color(0xFF1F5C99)
    // Pink scale from WetterOnline's radar precipitation legend (light → heavy).
    HourKind.LIGHT_SNOW -> Color(0xFFFFC2FF)
    HourKind.SNOW -> Color(0xFFF969BF)
    HourKind.HEAVY_SNOW -> Color(0xFFEA39A1)
    // Legacy night values stored by older installs render like plain cloud.
    else -> Color(0xFFE0E0E0)
}

internal fun HourKind.label(): Int = when (this) {
    HourKind.SUN -> R.string.hour_sun
    HourKind.PARTLY_SUN -> R.string.hour_partly_sun
    HourKind.CLOUDY -> R.string.hour_cloudy
    HourKind.OVERCAST -> R.string.hour_overcast
    HourKind.LIGHT_RAIN -> R.string.hour_light_rain
    HourKind.RAIN -> R.string.hour_rain
    HourKind.HEAVY_RAIN -> R.string.hour_heavy_rain
    HourKind.LIGHT_SNOW -> R.string.hour_light_snow
    HourKind.SNOW -> R.string.hour_snow
    HourKind.HEAVY_SNOW -> R.string.hour_heavy_snow
    else -> R.string.hour_cloudy
}
