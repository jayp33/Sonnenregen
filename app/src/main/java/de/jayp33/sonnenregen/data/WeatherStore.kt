package de.jayp33.sonnenregen.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.jayp33.sonnenregen.domain.DEFAULT_CITY
import de.jayp33.sonnenregen.domain.Forecast
import de.jayp33.sonnenregen.domain.NotificationText
import kotlinx.coroutines.flow.first
import java.time.ZoneId

private val Context.weatherDataStore by preferencesDataStore(name = "weather")

data class StoredWeather(
    val city: String,
    val compactSummary: String,
    val sunSummary: String,
    val rainSummary: String,
    val fetchedAtMillis: Long,
)

class WeatherStore(context: Context) {
    private val dataStore = context.applicationContext.weatherDataStore

    suspend fun save(forecast: Forecast): StoredWeather {
        val compact = NotificationText.compact(forecast)
        val fetchedAtMillis = forecast.fetchedAt.atZoneSameInstant(ZoneId.of("UTC")).toInstant().toEpochMilli()
        dataStore.edit { prefs ->
            prefs[CITY] = forecast.city
            prefs[COMPACT_SUMMARY] = compact
            prefs[SUN_SUMMARY] = forecast.sun.summary
            prefs[RAIN_SUMMARY] = forecast.rain.summary
            prefs[FETCHED_AT] = fetchedAtMillis
        }
        return StoredWeather(
            city = forecast.city,
            compactSummary = compact,
            sunSummary = forecast.sun.summary,
            rainSummary = forecast.rain.summary,
            fetchedAtMillis = fetchedAtMillis,
        )
    }

    suspend fun load(): StoredWeather? = dataStore.data.first().let { prefs ->
        val city = prefs[CITY] ?: return null
        val sun = prefs[SUN_SUMMARY] ?: return null
        val rain = prefs[RAIN_SUMMARY] ?: return null
        val fetchedAt = prefs[FETCHED_AT] ?: return null
        val compact = prefs[COMPACT_SUMMARY] ?: sun
        StoredWeather(city, compact, sun, rain, fetchedAt)
    }

    suspend fun isEnabled(): Boolean = dataStore.data.first()[ENABLED] ?: true

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[ENABLED] = enabled }
    }

    suspend fun city(): String = dataStore.data.first()[CITY] ?: DEFAULT_CITY

    private companion object {
        val CITY = stringPreferencesKey("city")
        val COMPACT_SUMMARY = stringPreferencesKey("compact_summary")
        val SUN_SUMMARY = stringPreferencesKey("sun_summary")
        val RAIN_SUMMARY = stringPreferencesKey("rain_summary")
        val FETCHED_AT = longPreferencesKey("fetched_at")
        val ENABLED = booleanPreferencesKey("enabled")
    }
}
