package de.jayp33.sonnenregen.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import de.jayp33.sonnenregen.R
import de.jayp33.sonnenregen.data.CityForecast
import de.jayp33.sonnenregen.data.WeatherStore
import de.jayp33.sonnenregen.domain.displayCity
import de.jayp33.sonnenregen.domain.isValidCitySlug
import de.jayp33.sonnenregen.domain.normalizeCity
import de.jayp33.sonnenregen.notification.WeatherNotifier
import de.jayp33.sonnenregen.work.WeatherScheduler
import kotlinx.coroutines.launch

@Composable
fun WeatherScreen() {
    val context = LocalContext.current
    val store = remember { WeatherStore(context) }
    val scope = rememberCoroutineScope()

    var cities by remember { mutableStateOf<List<String>>(emptyList()) }
    var forecasts by remember { mutableStateOf<Map<String, CityForecast>>(emptyMap()) }
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

    suspend fun reload() {
        cities = store.cities()
        forecasts = store.forecasts()
    }

    LaunchedEffect(Unit) {
        reload()
        enabled = store.isEnabled()
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
            )

            Text(stringResource(R.string.cities), style = MaterialTheme.typography.titleMedium)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = cityInput,
                    onValueChange = { cityInput = it; error = null },
                    label = { Text(stringResource(R.string.city_hint)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = {
                    scope.launch {
                        val city = normalizeCity(cityInput)
                        error = when {
                            !isValidCitySlug(city) -> context.getString(R.string.invalid_city)
                            city in cities -> context.getString(R.string.city_already_added)
                            !store.addCity(city) -> context.getString(R.string.invalid_city)
                            else -> {
                                cityInput = ""
                                reload()
                                WeatherScheduler.refreshNow(context)
                                null
                            }
                        }
                    }
                }) {
                    Text(stringResource(R.string.add_city))
                }
            }

            if (error != null) {
                Text(error!!, color = MaterialTheme.colorScheme.error)
            }

            if (cities.isEmpty()) {
                Text(stringResource(R.string.no_cities))
            } else {
                cities.forEach { city ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(R.string.city, displayCity(city)),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        val forecast = forecasts[city]
                        if (forecast != null) {
                            Text(stringResource(R.string.sun, forecast.sunSummary))
                            Text(stringResource(R.string.rain, forecast.rainSummary))
                        } else {
                            Text(stringResource(R.string.no_forecast))
                        }
                        Button(onClick = {
                            scope.launch {
                                store.removeCity(city)
                                reload()
                                if (enabled) WeatherNotifier.show(context, store.statuses())
                            }
                        }) {
                            Text(stringResource(R.string.remove))
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
