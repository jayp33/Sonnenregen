package de.jayp33.sonnenregen.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.jayp33.sonnenregen.domain.DEFAULT_CITY
import de.jayp33.sonnenregen.domain.Forecast
import de.jayp33.sonnenregen.domain.CLOCK_TIME
import de.jayp33.sonnenregen.domain.isValidCitySlug
import de.jayp33.sonnenregen.domain.normalizeCity
import de.jayp33.sonnenregen.text.ForecastFormatter
import de.jayp33.sonnenregen.text.ForecastPhrases
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

private val Context.weatherDataStore by preferencesDataStore(name = "weather")

class WeatherStore(context: Context) {
    private val appContext = context.applicationContext
    private val dataStore = appContext.weatherDataStore
    private val phrases = ForecastPhrases.from(appContext)
    private val json = Json { ignoreUnknownKeys = true }

    /** Cities in the order the user added them. */
    suspend fun cities(): List<String> = dataStore.data.first().let(::citiesFrom)

    /** Returns false for an invalid slug or one that is already configured. */
    suspend fun addCity(input: String): Boolean {
        val city = normalizeCity(input)
        if (!isValidCitySlug(city) || city in cities()) return false
        dataStore.edit { prefs ->
            val current = citiesFrom(prefs)
            if (city !in current) {
                prefs[CITIES] = json.encodeToString(current + city)
            }
        }
        return true
    }

    suspend fun removeCity(city: String) {
        dataStore.edit { prefs ->
            prefs[CITIES] = json.encodeToString(citiesFrom(prefs).filterNot { it == city })
            prefs[FORECASTS] = json.encodeToString(forecastsFrom(prefs).filterKeys { it != city })
        }
    }

    suspend fun forecasts(): Map<String, CityForecast> = dataStore.data.first().let(::forecastsFrom)

    /** Replace only the supplied cities; failed cities keep their last good forecast. */
    suspend fun saveForecasts(updates: List<CityForecast>) {
        if (updates.isEmpty()) return
        dataStore.edit { prefs ->
            prefs[FORECASTS] = json.encodeToString(mergeForecasts(forecastsFrom(prefs), updates))
        }
    }

    suspend fun statuses(staleCities: Set<String> = emptySet()): List<CityStatus> =
        buildStatuses(cities(), forecasts(), staleCities)

    fun format(forecast: Forecast): CityForecast = CityForecast(
        city = forecast.city,
        compactSummary = ForecastFormatter.compact(forecast, phrases),
        sunSummary = ForecastFormatter.verbose(forecast.sun, phrases),
        rainSummary = ForecastFormatter.verbose(forecast.rain, phrases),
        fetchedAtMillis = forecast.fetchedAt.toInstant().toEpochMilli(),
        sunriseLabel = forecast.sunrise?.format(CLOCK_TIME),
        sunsetLabel = forecast.sunset?.format(CLOCK_TIME),
    )

    suspend fun isEnabled(): Boolean = dataStore.data.first()[ENABLED] ?: true

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[ENABLED] = enabled }
    }

    private fun citiesFrom(prefs: Preferences): List<String> {
        val encoded = prefs[CITIES] ?: return listOf(legacyCity(prefs) ?: DEFAULT_CITY)
        return decodeCities(encoded)
    }

    private fun forecastsFrom(prefs: Preferences): Map<String, CityForecast> {
        val encoded = prefs[FORECASTS] ?: return legacyForecast(prefs)
        return decodeForecasts(encoded)
    }

    private fun decodeCities(value: String): List<String> = try {
        json.decodeFromString<List<String>>(value)
            .map(::normalizeCity)
            .filter(::isValidCitySlug)
            .distinct()
    } catch (_: SerializationException) {
        emptyList()
    } catch (_: IllegalArgumentException) {
        emptyList()
    }

    private fun decodeForecasts(value: String): Map<String, CityForecast> = try {
        json.decodeFromString<Map<String, CityForecast>>(value)
    } catch (_: SerializationException) {
        emptyMap()
    } catch (_: IllegalArgumentException) {
        emptyMap()
    }

    private fun legacyCity(prefs: Preferences): String? =
        prefs[LEGACY_CITY]?.takeIf(::isValidCitySlug)

    /** Import the original single-city installation without losing its forecast. */
    private fun legacyForecast(prefs: Preferences): Map<String, CityForecast> {
        val city = legacyCity(prefs) ?: return emptyMap()
        val sun = prefs[LEGACY_SUN] ?: return emptyMap()
        val rain = prefs[LEGACY_RAIN] ?: return emptyMap()
        val fetchedAt = prefs[LEGACY_FETCHED_AT] ?: return emptyMap()
        val compact = prefs[LEGACY_COMPACT] ?: sun
        return mapOf(city to CityForecast(city, compact, sun, rain, fetchedAt))
    }

    private companion object {
        val CITIES = stringPreferencesKey("cities")
        val FORECASTS = stringPreferencesKey("forecasts_by_city")
        val ENABLED = booleanPreferencesKey("enabled")

        // Original single-city schema, read once for migration.
        val LEGACY_CITY = stringPreferencesKey("city")
        val LEGACY_COMPACT = stringPreferencesKey("compact_summary")
        val LEGACY_SUN = stringPreferencesKey("sun_summary")
        val LEGACY_RAIN = stringPreferencesKey("rain_summary")
        val LEGACY_FETCHED_AT = longPreferencesKey("fetched_at")
    }
}
