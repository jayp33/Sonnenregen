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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** Move one city by +/-1 without touching the others or allowing out-of-range moves. */
internal fun reorderCity(cities: List<String>, city: String, offset: Int): List<String> {
    val from = cities.indexOf(city)
    val to = from + offset
    if (from < 0 || to !in cities.indices) return cities
    return cities.toMutableList().apply { add(to, removeAt(from)) }
}

/** Add/remove one city from the notification-only hide list. */
internal fun updateNotificationVisibility(
    hidden: Set<String>,
    city: String,
    visible: Boolean,
): Set<String> = if (visible) hidden - city else hidden + city

private val Context.weatherDataStore by preferencesDataStore(name = "weather")

internal val cityJson = Json { ignoreUnknownKeys = true }

/**
 * Keep every non-blank stored entry, even if it is no longer a valid slug.
 * Invalid entries would otherwise disappear from the UI together with their
 * Remove button, leaving a broken city undeletable.
 */
internal fun decodeCities(value: String, json: Json = cityJson): List<String> = try {
    json.decodeFromString<List<String>>(value)
        .map(::normalizeCity)
        .filter(String::isNotBlank)
        .distinct()
} catch (_: SerializationException) {
    emptyList()
} catch (_: IllegalArgumentException) {
    emptyList()
}

internal fun decodeHiddenCities(value: String, json: Json = cityJson): Set<String> = try {
    json.decodeFromString<List<String>>(value)
        .map(::normalizeCity)
        .filter(String::isNotBlank)
        .toSet()
} catch (_: SerializationException) {
    emptySet()
} catch (_: IllegalArgumentException) {
    emptySet()
}

/** Decode each city independently so one corrupt entry cannot erase the rest. */
internal fun decodeForecasts(value: String, json: Json = cityJson): Map<String, CityForecast> = try {
    json.decodeFromString<JsonObject>(value)
        .mapNotNull { (key, element) ->
            try {
                normalizeCity(key) to json.decodeFromString<CityForecast>(element.toString())
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }
        .toMap()
} catch (_: SerializationException) {
    emptyMap()
} catch (_: IllegalArgumentException) {
    emptyMap()
}

class WeatherStore(context: Context) {
    private val appContext = context.applicationContext
    private val dataStore = appContext.weatherDataStore
    private val phrases = ForecastPhrases.from(appContext)
    private val json = cityJson

    /** Reactive views for the UI; worker updates flow through the same DataStore. */
    val citiesFlow: Flow<List<String>> = dataStore.data.map(::citiesFrom)
    val forecastsFlow: Flow<Map<String, CityForecast>> = dataStore.data.map(::forecastsFrom)
    val hiddenCitiesFlow: Flow<Set<String>> = dataStore.data.map(::hiddenCitiesFrom)

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
            prefs[HIDDEN_CITIES] = json.encodeToString(hiddenCitiesFrom(prefs) - city)
        }
    }

    /** Swap a city with its neighbour; the UI observes the new order immediately. */
    suspend fun moveCity(city: String, offset: Int) {
        dataStore.edit { prefs ->
            val current = citiesFrom(prefs)
            val next = reorderCity(current, city, offset)
            if (next != current) prefs[CITIES] = json.encodeToString(next)
        }
    }

    /** Hide/show one city in the notification without removing it from the app. */
    suspend fun setCityNotificationVisible(city: String, visible: Boolean) {
        dataStore.edit { prefs ->
            val next = updateNotificationVisibility(hiddenCitiesFrom(prefs), city, visible)
            prefs[HIDDEN_CITIES] = json.encodeToString(next.toList())
        }
    }

    suspend fun forecasts(): Map<String, CityForecast> = dataStore.data.first().let(::forecastsFrom)

    suspend fun hiddenCities(): Set<String> = dataStore.data.first().let(::hiddenCitiesFrom)

    /** Replace only the supplied cities; failed cities keep their last good forecast. */
    suspend fun saveForecasts(updates: List<CityForecast>) {
        if (updates.isEmpty()) return
        dataStore.edit { prefs ->
            prefs[FORECASTS] = json.encodeToString(mergeForecasts(forecastsFrom(prefs), updates))
        }
    }

    suspend fun statuses(staleCities: Set<String> = emptySet()): List<CityStatus> =
        buildStatuses(cities(), forecasts(), staleCities, hiddenCities())

    fun format(forecast: Forecast): CityForecast = CityForecast(
        city = forecast.city,
        compactSummary = ForecastFormatter.compact(forecast, phrases),
        sunSummary = ForecastFormatter.verbose(forecast.sun, phrases),
        rainSummary = ForecastFormatter.verboseRain(forecast, phrases),
        fetchedAtMillis = forecast.fetchedAt.toInstant().toEpochMilli(),
        displayName = forecast.displayName,
        sunriseLabel = forecast.sunrise?.format(CLOCK_TIME),
        sunsetLabel = forecast.sunset?.format(CLOCK_TIME),
        hours = forecast.hours,
        nowcastCells = forecast.nowcastCells,
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

    private fun hiddenCitiesFrom(prefs: Preferences): Set<String> =
        prefs[HIDDEN_CITIES]?.let(::decodeHiddenCities) ?: emptySet()

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
        val HIDDEN_CITIES = stringPreferencesKey("hidden_from_notification")
        val ENABLED = booleanPreferencesKey("enabled")

        // Original single-city schema, read once for migration.
        val LEGACY_CITY = stringPreferencesKey("city")
        val LEGACY_COMPACT = stringPreferencesKey("compact_summary")
        val LEGACY_SUN = stringPreferencesKey("sun_summary")
        val LEGACY_RAIN = stringPreferencesKey("rain_summary")
        val LEGACY_FETCHED_AT = longPreferencesKey("fetched_at")
    }
}
