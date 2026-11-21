package de.jayp33.sonnenregen.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import de.jayp33.sonnenregen.R
import de.jayp33.sonnenregen.data.CityForecast
import de.jayp33.sonnenregen.data.WeatherStore
import de.jayp33.sonnenregen.domain.displayCity
import de.jayp33.sonnenregen.domain.isValidCitySlug
import de.jayp33.sonnenregen.domain.normalizeCity
import de.jayp33.sonnenregen.notification.WeatherNotifier
import de.jayp33.sonnenregen.text.AgeUnit
import de.jayp33.sonnenregen.text.relativeAge
import de.jayp33.sonnenregen.work.WeatherScheduler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun WeatherScreen() {
    val context = LocalContext.current
    val store = remember { WeatherStore(context) }
    val scope = rememberCoroutineScope()

    var cities by remember { mutableStateOf<List<String>>(emptyList()) }
    var forecasts by remember { mutableStateOf<Map<String, CityForecast>>(emptyMap()) }
    var expandedCities by remember { mutableStateOf<Set<String>>(emptySet()) }
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var showAddCityDialog by remember { mutableStateOf(false) }
    var cityInput by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var enabled by remember { mutableStateOf(true) }
    var notificationGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> notificationGranted = granted }

    LaunchedEffect(Unit) {
        enabled = store.isEnabled()
        launch { store.citiesFlow.collect { cities = it } }
        launch { store.forecastsFlow.collect { forecasts = it } }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            nowMillis = System.currentTimeMillis()
        }
    }

    // Oldest successful fetch, matching the notification's global timestamp.
    val oldestFetchedAt = forecasts.values.map { it.fetchedAtMillis }
        .filter { it > 0L }
        .minOrNull()

    fun dismissAddCity() {
        showAddCityDialog = false
        cityInput = ""
        error = null
    }

    Scaffold(
        floatingActionButton = {
            val addCityLabel = stringResource(R.string.add_city)
            FloatingActionButton(
                onClick = {
                    cityInput = ""
                    error = null
                    showAddCityDialog = true
                },
                modifier = Modifier.semantics(mergeDescendants = true) {
                    contentDescription = addCityLabel
                },
            ) {
                Text("+", style = MaterialTheme.typography.titleLarge)
            }
        },
    ) { padding ->
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    if (oldestFetchedAt != null) {
                        UpdatedText(oldestFetchedAt, nowMillis)
                    }
                }

                Text(stringResource(R.string.cities), style = MaterialTheme.typography.titleMedium)

                if (cities.isEmpty()) {
                    Text(stringResource(R.string.no_cities))
                } else {
                    cities.forEach { city ->
                        val forecast = forecasts[city]
                        val label = forecast?.displayName?.takeIf { it.isNotBlank() }
                            ?: displayCity(city)
                        val detailsExpanded = city in expandedCities

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            // The city line and colour grid are one tappable summary;
                            // no separate show/hide button is needed.
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        expandedCities = if (detailsExpanded) {
                                            expandedCities - city
                                        } else {
                                            expandedCities + city
                                        }
                                    },
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    stringResource(R.string.city, label),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                if (forecast != null) {
                                    HourlyOverview(label, forecast.hours)
                                } else {
                                    Text(stringResource(R.string.no_forecast))
                                }
                            }

                            if (forecast != null && detailsExpanded) {
                                UpdatedText(forecast.fetchedAtMillis, nowMillis)
                                Text(stringResource(R.string.sun, forecast.sunSummary))
                                Text(stringResource(R.string.rain, forecast.rainSummary))
                                val sunrise = forecast.sunriseLabel?.let {
                                    stringResource(R.string.sunrise, it)
                                }
                                val sunset = forecast.sunsetLabel?.let {
                                    stringResource(R.string.sunset, it)
                                }
                                if (sunrise != null || sunset != null) {
                                    Text(listOfNotNull(sunrise, sunset).joinToString(" · "))
                                }

                                Button(onClick = {
                                    scope.launch {
                                        store.removeCity(city)
                                        if (enabled) WeatherNotifier.show(context, store.statuses())
                                    }
                                }) {
                                    Text(stringResource(R.string.remove))
                                }
                            }
                        }
                    }
                }

                Button(onClick = { WeatherScheduler.refreshNow(context) }) {
                    Text(stringResource(R.string.refresh_now))
                }

                Button(
                    onClick = {
                        scope.launch {
                            val next = !enabled
                            store.setEnabled(next)
                            if (next) {
                                WeatherScheduler.schedule(context)
                            } else {
                                WeatherScheduler.cancel(context)
                                WeatherNotifier.cancel(context)
                            }
                            enabled = next
                        }
                    },
                ) {
                    Text(
                        stringResource(
                            if (enabled) R.string.disable_notification else R.string.enable_notification,
                        ),
                    )
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    !notificationGranted
                ) {
                    Button(onClick = {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }) {
                        Text(stringResource(R.string.allow_notifications))
                    }
                }
            }
        }
    }

    if (showAddCityDialog) {
        AlertDialog(
            onDismissRequest = ::dismissAddCity,
            title = { Text(stringResource(R.string.add_city)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = cityInput,
                        onValueChange = { cityInput = it; error = null },
                        label = { Text(stringResource(R.string.city_hint)) },
                        singleLine = true,
                    )
                    if (error != null) {
                        Text(error!!, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val city = normalizeCity(cityInput)
                        error = when {
                            !isValidCitySlug(city) -> context.getString(R.string.invalid_city)
                            city in cities -> context.getString(R.string.city_already_added)
                            !store.addCity(city) -> context.getString(R.string.invalid_city)
                            else -> {
                                dismissAddCity()
                                WeatherScheduler.refreshNow(context)
                                null
                            }
                        }
                    }
                }) {
                    Text(stringResource(R.string.add_city))
                }
            },
            dismissButton = {
                TextButton(onClick = ::dismissAddCity) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun UpdatedText(fetchedAtMillis: Long, nowMillis: Long) {
    val age = relativeAge(fetchedAtMillis, nowMillis) ?: return
    val text = when (age.unit) {
        AgeUnit.JUST_NOW -> stringResource(R.string.updated_just_now)
        AgeUnit.MINUTES -> stringResource(R.string.updated_minutes_ago, age.value)
        AgeUnit.HOURS -> stringResource(R.string.updated_hours_ago, age.value)
        AgeUnit.DAYS -> if (age.value == 1L) {
            stringResource(R.string.updated_day_ago)
        } else {
            stringResource(R.string.updated_days_ago, age.value)
        }
    }
    Text(text, style = MaterialTheme.typography.labelSmall)
}
