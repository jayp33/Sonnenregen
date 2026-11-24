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
import de.jayp33.sonnenregen.notification.AlertKey
import de.jayp33.sonnenregen.notification.AlertKind
import de.jayp33.sonnenregen.notification.AlertStage
import de.jayp33.sonnenregen.notification.AlertState
import de.jayp33.sonnenregen.domain.normalizeCity
import de.jayp33.sonnenregen.domain.toSnapshot
import de.jayp33.sonnenregen.text.ForecastFormatter
import de.jayp33.sonnenregen.text.ForecastPhrases
import de.jayp33.sonnenregen.text.ForecastText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** Move one city by +/-1 without touching the others or allowing out-of-range moves. */
internal fun reorderCity(cities: List<String>, city: String, offset: Int): List<String> {
    val from = cities.indexOf(city)
    val to = from + offset
    if (from < 0 || to !in cities.indices) return cities
    return cities.toMutableList().apply { add(to, removeAt(from)) }
}

/** Add/remove one city from a per-city preference set. */
internal fun updateCityInSet(
    cities: Set<String>,
    city: String,
    enabled: Boolean,
): Set<String> = if (enabled) cities + city else cities - city

/** Add/remove one city from the notification-only hide list. */
internal fun updateNotificationVisibility(
    hidden: Set<String>,
    city: String,
    visible: Boolean,
): Set<String> = updateCityInSet(hidden, city, !visible)

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

internal fun decodeCitySet(value: String, json: Json = cityJson): Set<String> = try {
    json.decodeFromString<List<String>>(value)
        .map(::normalizeCity)
        .filter(String::isNotBlank)
        .toSet()
} catch (_: SerializationException) {
    emptySet()
} catch (_: IllegalArgumentException) {
    emptySet()
}

internal fun decodeHiddenCities(value: String, json: Json = cityJson): Set<String> =
    decodeCitySet(value, json)

/** Lead times offered in the UI, also the only values accepted from storage. */
val ALERT_LEAD_OPTIONS = listOf(0, 15, 30, 60)

internal fun decodeLeadMinutes(value: String?, json: Json = cityJson): Map<String, Int> {
    if (value == null) return emptyMap()
    return try {
        json.decodeFromString<JsonObject>(value)
            .mapNotNull { (key, element) ->
                val city = normalizeCity(key)
                val minutes = element.toString().toIntOrNull()
                if (city.isBlank() || minutes == null || minutes !in ALERT_LEAD_OPTIONS) {
                    null
                } else {
                    city to minutes
                }
            }
            .toMap()
    } catch (_: SerializationException) {
        emptyMap()
    } catch (_: IllegalArgumentException) {
        emptyMap()
    }
}

internal fun decodeAlertStates(value: String?, json: Json = cityJson): Map<String, AlertState> {
    if (value == null) return emptyMap()
    return try {
        json.decodeFromString<Map<String, AlertState>>(value).filterKeys(String::isNotBlank)
    } catch (_: SerializationException) {
        emptyMap()
    } catch (_: IllegalArgumentException) {
        emptyMap()
    }
}

/**
 * Apply a planned state update only to keys that have not changed since the
 * planner read them. A scheduled alert can fire while a refresh is running and
 * mark itself NOTIFIED; writing the planner's stale SCHEDULED state over that
 * would re-enqueue the same alert and post it twice.
 */
internal fun mergeAlertStates(
    current: Map<String, AlertState>,
    previous: Map<String, AlertState>,
    next: Map<String, AlertState>,
): Map<String, AlertState> {
    val merged = current.toMutableMap()
    for ((id, state) in next) {
        if (current[id] == previous[id]) merged[id] = state
    }
    return merged
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
    val sunAlertCitiesFlow: Flow<Set<String>> = dataStore.data.map { citySetFrom(it, SUN_ALERTS) }
    val rainAlertCitiesFlow: Flow<Set<String>> = dataStore.data.map { citySetFrom(it, RAIN_ALERTS) }
    val snowAlertCitiesFlow: Flow<Set<String>> = dataStore.data.map { citySetFrom(it, SNOW_ALERTS) }
    val sunLeadMinutesFlow: Flow<Map<String, Int>> =
        dataStore.data.map { decodeLeadMinutes(it[SUN_LEAD_MINUTES]) }
    val rainLeadMinutesFlow: Flow<Map<String, Int>> =
        dataStore.data.map { decodeLeadMinutes(it[RAIN_LEAD_MINUTES]) }
    val snowLeadMinutesFlow: Flow<Map<String, Int>> =
        dataStore.data.map { decodeLeadMinutes(it[SNOW_LEAD_MINUTES]) }

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
            prefs[SUN_ALERTS] = json.encodeToString(citySetFrom(prefs, SUN_ALERTS) - city)
            prefs[RAIN_ALERTS] = json.encodeToString(citySetFrom(prefs, RAIN_ALERTS) - city)
            prefs[SNOW_ALERTS] = json.encodeToString(citySetFrom(prefs, SNOW_ALERTS) - city)
            prefs[SUN_LEAD_MINUTES] =
                json.encodeToString(decodeLeadMinutes(prefs[SUN_LEAD_MINUTES]) - city)
            prefs[RAIN_LEAD_MINUTES] =
                json.encodeToString(decodeLeadMinutes(prefs[RAIN_LEAD_MINUTES]) - city)
            prefs[SNOW_LEAD_MINUTES] =
                json.encodeToString(decodeLeadMinutes(prefs[SNOW_LEAD_MINUTES]) - city)
            val states = statesFrom(prefs).toMutableMap()
            AlertKind.entries.forEach { kind -> states -= AlertKey(city, kind).id }
            prefs[ALERT_STATES] = json.encodeToString(states.toMap())
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

    /** Enable or disable one per-city sun/rain/snow alert. Off by default for every city. */
    suspend fun setCityAlert(city: String, kind: AlertKind, enabled: Boolean) {
        val key = when (kind) {
            AlertKind.SUN -> SUN_ALERTS
            AlertKind.RAIN -> RAIN_ALERTS
            AlertKind.SNOW -> SNOW_ALERTS
        }
        dataStore.edit { prefs ->
            val next = updateCityInSet(citySetFrom(prefs, key), city, enabled)
            prefs[key] = json.encodeToString(next.toList())
            if (!enabled) {
                // Invalidate any queued job immediately; the next refresh would
                // reset this anyway, but a job can fire before it gets there.
                val states = statesFrom(prefs).toMutableMap()
                states[AlertKey(city, kind).id] = AlertState()
                prefs[ALERT_STATES] = json.encodeToString(states.toMap())
            }
        }
    }

    /** Persist the per-condition lead time (in minutes) for one city. */
    suspend fun setAlertLead(city: String, kind: AlertKind, minutes: Int) {
        require(minutes in ALERT_LEAD_OPTIONS) { "Unsupported alert lead time: $minutes" }
        val key = when (kind) {
            AlertKind.SUN -> SUN_LEAD_MINUTES
            AlertKind.RAIN -> RAIN_LEAD_MINUTES
            AlertKind.SNOW -> SNOW_LEAD_MINUTES
        }
        dataStore.edit { prefs ->
            val next = decodeLeadMinutes(prefs[key]).toMutableMap().apply { put(city, minutes) }
            prefs[key] = json.encodeToString(next.toMap())
        }
    }

    /** Remember that an alert episode was posted so later refreshes don't repeat it. */
    suspend fun markAlertNotified(key: AlertKey, eventAtMillis: Long) {
        dataStore.edit { prefs ->
            val states = statesFrom(prefs).toMutableMap()
            val previous = states[key.id]
            states[key.id] = AlertState(
                stage = AlertStage.NOTIFIED,
                eventAtMillis = eventAtMillis,
                episodeEnded = previous?.episodeEnded ?: false,
            )
            prefs[ALERT_STATES] = json.encodeToString(states.toMap())
        }
    }

    suspend fun alertStates(): Map<String, AlertState> =
        dataStore.data.first().let { statesFrom(it) }

    /** Forget every episode so re-enabling alerts never replays an old one. */
    suspend fun clearAlertStates() {
        dataStore.edit { prefs -> prefs[ALERT_STATES] = json.encodeToString(emptyMap<String, AlertState>()) }
    }

    /** Save a plan only where its baseline still matches the stored state. */
    suspend fun saveAlertStates(
        previous: Map<String, AlertState>,
        next: Map<String, AlertState>,
    ) {
        dataStore.edit { prefs ->
            val merged = mergeAlertStates(statesFrom(prefs), previous, next)
            prefs[ALERT_STATES] = json.encodeToString(merged)
        }
    }

    suspend fun forecasts(): Map<String, CityForecast> = dataStore.data.first().let(::forecastsFrom)

    suspend fun hiddenCities(): Set<String> = dataStore.data.first().let(::hiddenCitiesFrom)

    suspend fun sunAlertCities(): Set<String> = dataStore.data.first().let { citySetFrom(it, SUN_ALERTS) }

    suspend fun rainAlertCities(): Set<String> = dataStore.data.first().let { citySetFrom(it, RAIN_ALERTS) }

    suspend fun snowAlertCities(): Set<String> = dataStore.data.first().let { citySetFrom(it, SNOW_ALERTS) }

    suspend fun sunLeadMinutes(): Map<String, Int> =
        dataStore.data.first().let { decodeLeadMinutes(it[SUN_LEAD_MINUTES]) }

    suspend fun rainLeadMinutes(): Map<String, Int> =
        dataStore.data.first().let { decodeLeadMinutes(it[RAIN_LEAD_MINUTES]) }

    suspend fun snowLeadMinutes(): Map<String, Int> =
        dataStore.data.first().let { decodeLeadMinutes(it[SNOW_LEAD_MINUTES]) }

    /** Replace only the supplied cities; failed cities keep their last good forecast. */
    suspend fun saveForecasts(updates: List<CityForecast>) {
        if (updates.isEmpty()) return
        dataStore.edit { prefs ->
            prefs[FORECASTS] = json.encodeToString(mergeForecasts(forecastsFrom(prefs), updates))
        }
    }

    suspend fun statuses(
        staleCities: Set<String> = emptySet(),
        nowMillis: Long = System.currentTimeMillis(),
    ): List<CityStatus> = buildStatuses(
        cities = cities(),
        forecasts = forecasts(),
        staleCities = staleCities,
        hiddenCities = hiddenCities(),
        nowMillis = nowMillis,
        compactFor = { ForecastText.compact(it, nowMillis, phrases) },
    )

    fun format(forecast: Forecast): CityForecast = CityForecast(
        city = forecast.city,
        compactSummary = ForecastFormatter.compact(forecast, phrases),
        sunSummary = ForecastFormatter.verbose(forecast.sun, phrases),
        rainSummary = ForecastFormatter.verboseRain(forecast, phrases),
        // Only surface a snow line when snow is actually expected, so the common
        // snow-free case does not add a permanent "no snow" row to every city.
        snowSummary = forecast.snow
            ?.takeIf { it.currently || it.minutes != null }
            ?.let { ForecastFormatter.verbose(it, phrases) }
            .orEmpty(),
        fetchedAtMillis = forecast.fetchedAt.toInstant().toEpochMilli(),
        displayName = forecast.displayName,
        timezone = forecast.timezone,
        sunriseLabel = forecast.sunrise?.format(CLOCK_TIME),
        sunsetLabel = forecast.sunset?.format(CLOCK_TIME),
        hours = forecast.hours,
        nowcastCells = forecast.nowcastCells,
        sunEvents = forecast.sunEvents,
        sunCurrently = forecast.sun.currently,
        rainCurrently = forecast.rain.currently,
        // Older stored forecasts have no snow change at all.
        snowCurrently = forecast.snow?.currently ?: false,
        // `at` is the next onset only while the condition is inactive; when it
        // is active the field describes the end, which is not an alert event.
        sunEventAtMillis = forecast.sun.at?.takeIf { !forecast.sun.currently }
            ?.toInstant()?.toEpochMilli(),
        rainEventAtMillis = forecast.rain.at?.takeIf { !forecast.rain.currently }
            ?.toInstant()?.toEpochMilli(),
        snowEventAtMillis = forecast.snow?.at?.takeIf { !forecast.snow.currently }
            ?.toInstant()?.toEpochMilli(),
        forecastReferenceMillis = forecast.now.toInstant().toEpochMilli(),
        sunChange = forecast.sun.toSnapshot(),
        rainChange = forecast.rain.toSnapshot(),
        snowChange = forecast.snow?.toSnapshot(),
        rainAgainAtMillis = forecast.rainOutlook.againInMinutes?.let {
            forecast.now.plusMinutes(it).toInstant().toEpochMilli()
        },
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

    private fun citySetFrom(prefs: Preferences, key: Preferences.Key<String>): Set<String> =
        prefs[key]?.let(::decodeCitySet) ?: emptySet()

    private fun statesFrom(prefs: Preferences): Map<String, AlertState> =
        decodeAlertStates(prefs[ALERT_STATES])

    private fun legacyCity(prefs: Preferences): String? =
        prefs[LEGACY_CITY]?.takeIf(::isValidCitySlug)

    /** Import the original single-city installation without losing its forecast. */
    private fun legacyForecast(prefs: Preferences): Map<String, CityForecast> {
        val city = legacyCity(prefs) ?: return emptyMap()
        val sun = prefs[LEGACY_SUN] ?: return emptyMap()
        val rain = prefs[LEGACY_RAIN] ?: return emptyMap()
        val fetchedAt = prefs[LEGACY_FETCHED_AT] ?: return emptyMap()
        val compact = prefs[LEGACY_COMPACT] ?: sun
        return mapOf(
            city to CityForecast(
                city = city,
                compactSummary = compact,
                sunSummary = sun,
                rainSummary = rain,
                fetchedAtMillis = fetchedAt,
            ),
        )
    }

    private companion object {
        val CITIES = stringPreferencesKey("cities")
        val FORECASTS = stringPreferencesKey("forecasts_by_city")
        val HIDDEN_CITIES = stringPreferencesKey("hidden_from_notification")
        val SUN_ALERTS = stringPreferencesKey("sun_alert_cities")
        val RAIN_ALERTS = stringPreferencesKey("rain_alert_cities")
        val SNOW_ALERTS = stringPreferencesKey("snow_alert_cities")
        val SUN_LEAD_MINUTES = stringPreferencesKey("sun_alert_lead_minutes")
        val RAIN_LEAD_MINUTES = stringPreferencesKey("rain_alert_lead_minutes")
        val SNOW_LEAD_MINUTES = stringPreferencesKey("snow_alert_lead_minutes")
        val ALERT_STATES = stringPreferencesKey("alert_states")
        val ENABLED = booleanPreferencesKey("enabled")

        // Original single-city schema, read once for migration.
        val LEGACY_CITY = stringPreferencesKey("city")
        val LEGACY_COMPACT = stringPreferencesKey("compact_summary")
        val LEGACY_SUN = stringPreferencesKey("sun_summary")
        val LEGACY_RAIN = stringPreferencesKey("rain_summary")
        val LEGACY_FETCHED_AT = longPreferencesKey("fetched_at")
    }
}
