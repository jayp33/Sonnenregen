package de.jayp33.sonnenregen.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import de.jayp33.sonnenregen.R
import de.jayp33.sonnenregen.data.ALERT_LEAD_OPTIONS
import de.jayp33.sonnenregen.data.CityForecast
import de.jayp33.sonnenregen.data.WeatherStore
import de.jayp33.sonnenregen.domain.cityWebsiteUrl
import de.jayp33.sonnenregen.domain.currentOrFutureHours
import de.jayp33.sonnenregen.domain.currentOrFutureNowcast
import de.jayp33.sonnenregen.domain.displayCity
import de.jayp33.sonnenregen.domain.isRain
import de.jayp33.sonnenregen.domain.isSnow
import de.jayp33.sonnenregen.domain.isSun
import de.jayp33.sonnenregen.domain.isValidCitySlug
import de.jayp33.sonnenregen.domain.normalizeCity
import de.jayp33.sonnenregen.notification.AlertKey
import de.jayp33.sonnenregen.notification.AlertKind
import de.jayp33.sonnenregen.notification.BatteryOptimizations
import de.jayp33.sonnenregen.notification.WeatherNotifier
import de.jayp33.sonnenregen.text.AgeUnit
import de.jayp33.sonnenregen.text.relativeAge
import de.jayp33.sonnenregen.widget.updateWeatherWidget
import de.jayp33.sonnenregen.work.WeatherScheduler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun WeatherScreen() {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val store = remember { WeatherStore(context) }
    val scope = rememberCoroutineScope()

    var cities by remember { mutableStateOf<List<String>>(emptyList()) }
    var forecasts by remember { mutableStateOf<Map<String, CityForecast>>(emptyMap()) }
    var hiddenCities by remember { mutableStateOf<Set<String>>(emptySet()) }
    var widgetCity by remember { mutableStateOf<String?>(null) }
    var sunAlertCities by remember { mutableStateOf<Set<String>>(emptySet()) }
    var rainAlertCities by remember { mutableStateOf<Set<String>>(emptySet()) }
    var snowAlertCities by remember { mutableStateOf<Set<String>>(emptySet()) }
    var sunLeadMinutes by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var rainLeadMinutes by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var snowLeadMinutes by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
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
    ) { granted ->
        notificationGranted = granted
        if (granted) {
            // Android only posts through WorkManager/BootReceiver otherwise, so
            // granting permission mid-session would otherwise look like nothing
            // happened until the next refresh.
            scope.launch {
                if (store.isEnabled()) {
                    WeatherNotifier.show(context, store.statuses())
                    WeatherScheduler.refreshNow(context)
                }
            }
        }
    }
    var ignoringBatteryOptimizations by remember {
        mutableStateOf(BatteryOptimizations.isIgnoring(context))
    }
    val batteryOptimizationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        // The system dialog runs outside the app, so read the state on return.
        ignoringBatteryOptimizations = BatteryOptimizations.isIgnoring(context)
    }

    LaunchedEffect(Unit) {
        enabled = store.isEnabled()
        if (enabled) {
            WeatherScheduler.schedule(context)
            if (notificationGranted) WeatherNotifier.show(context, store.statuses())
        }
        launch { store.citiesFlow.collect { cities = it } }
        launch { store.forecastsFlow.collect { forecasts = it } }
        launch { store.hiddenCitiesFlow.collect { hiddenCities = it } }
        launch { store.widgetCityFlow.collect { widgetCity = it } }
        launch { store.sunAlertCitiesFlow.collect { sunAlertCities = it } }
        launch { store.rainAlertCitiesFlow.collect { rainAlertCities = it } }
        launch { store.snowAlertCitiesFlow.collect { snowAlertCities = it } }
        launch { store.sunLeadMinutesFlow.collect { sunLeadMinutes = it } }
        launch { store.rainLeadMinutesFlow.collect { rainLeadMinutes = it } }
        launch { store.snowLeadMinutesFlow.collect { snowLeadMinutes = it } }
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
                    cities.forEachIndexed { index, city ->
                        val forecast = forecasts[city]
                        val label = forecast?.displayName?.takeIf { it.isNotBlank() }
                            ?: displayCity(city)

                        CityItem(
                            label = label,
                            index = index,
                            cityCount = cities.size,
                            forecast = forecast,
                            detailsExpanded = city in expandedCities,
                            hidden = city in hiddenCities,
                            widgetSelected = widgetCity == city,
                            sunAlert = city in sunAlertCities,
                            rainAlert = city in rainAlertCities,
                            snowAlert = city in snowAlertCities,
                            sunLeadMinutes = sunLeadMinutes[city] ?: 0,
                            rainLeadMinutes = rainLeadMinutes[city] ?: 0,
                            snowLeadMinutes = snowLeadMinutes[city] ?: 0,
                            nowMillis = nowMillis,
                            onOpenWebsite = { uriHandler.openUri(cityWebsiteUrl(city)) },
                            onToggleExpanded = {
                                expandedCities = if (city in expandedCities) {
                                    expandedCities - city
                                } else {
                                    expandedCities + city
                                }
                            },
                            onToggleNotificationVisibility = { visible ->
                                scope.launch {
                                    store.setCityNotificationVisible(city, visible)
                                    if (enabled) {
                                        WeatherNotifier.show(context, store.statuses())
                                    }
                                    updateWeatherWidget(context)
                                }
                            },
                            onToggleWidget = { selected ->
                                scope.launch {
                                    store.setWidgetCity(if (selected) city else null)
                                    updateWeatherWidget(context)
                                }
                            },
                            onToggleSunAlert = { on ->
                                scope.launch {
                                    store.setCityAlert(city, AlertKind.SUN, on)
                                    if (!on) {
                                        WeatherScheduler.cancelAlert(
                                            context,
                                            AlertKey(city, AlertKind.SUN),
                                        )
                                    }
                                    WeatherScheduler.refreshNow(context)
                                }
                            },
                            onToggleRainAlert = { on ->
                                scope.launch {
                                    store.setCityAlert(city, AlertKind.RAIN, on)
                                    if (!on) {
                                        WeatherScheduler.cancelAlert(
                                            context,
                                            AlertKey(city, AlertKind.RAIN),
                                        )
                                    }
                                    WeatherScheduler.refreshNow(context)
                                }
                            },
                            onSetSunLeadMinutes = { minutes ->
                                scope.launch {
                                    store.setAlertLead(city, AlertKind.SUN, minutes)
                                    WeatherScheduler.refreshNow(context)
                                }
                            },
                            onSetRainLeadMinutes = { minutes ->
                                scope.launch {
                                    store.setAlertLead(city, AlertKind.RAIN, minutes)
                                    WeatherScheduler.refreshNow(context)
                                }
                            },
                            onToggleSnowAlert = { on ->
                                scope.launch {
                                    store.setCityAlert(city, AlertKind.SNOW, on)
                                    if (!on) {
                                        WeatherScheduler.cancelAlert(
                                            context,
                                            AlertKey(city, AlertKind.SNOW),
                                        )
                                    }
                                    WeatherScheduler.refreshNow(context)
                                }
                            },
                            onSetSnowLeadMinutes = { minutes ->
                                scope.launch {
                                    store.setAlertLead(city, AlertKind.SNOW, minutes)
                                    WeatherScheduler.refreshNow(context)
                                }
                            },
                            onMove = { offset ->
                                scope.launch {
                                    store.moveCity(city, offset)
                                    if (enabled) {
                                        WeatherNotifier.show(context, store.statuses())
                                    }
                                    updateWeatherWidget(context)
                                }
                            },
                            onRemove = {
                                scope.launch {
                                    store.removeCity(city)
                                    WeatherScheduler.cancelAlert(
                                        context,
                                        AlertKey(city, AlertKind.SUN),
                                    )
                                    WeatherScheduler.cancelAlert(
                                        context,
                                        AlertKey(city, AlertKind.RAIN),
                                    )
                                    WeatherScheduler.cancelAlert(
                                        context,
                                        AlertKey(city, AlertKind.SNOW),
                                    )
                                    expandedCities = expandedCities - city
                                    if (enabled) WeatherNotifier.show(context, store.statuses())
                                    updateWeatherWidget(context)
                                }
                            },
                        )
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
                                store.clearAlertStates()
                                (sunAlertCities + rainAlertCities + snowAlertCities)
                                    .forEach { city ->
                                        WeatherScheduler.cancelAlert(
                                            context,
                                            AlertKey(city, AlertKind.SUN),
                                        )
                                        WeatherScheduler.cancelAlert(
                                            context,
                                            AlertKey(city, AlertKind.RAIN),
                                        )
                                        WeatherScheduler.cancelAlert(
                                            context,
                                            AlertKey(city, AlertKind.SNOW),
                                        )
                                    }
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

                BatteryOptimizationControl(
                    ignoring = ignoringBatteryOptimizations,
                    onRequest = {
                        try {
                            batteryOptimizationLauncher
                                .launch(BatteryOptimizations.requestIntent(context))
                        } catch (_: ActivityNotFoundException) {
                            batteryOptimizationLauncher
                                .launch(BatteryOptimizations.settingsIntent())
                        }
                    },
                )
            }
        }
    }

    if (showAddCityDialog) {
        AddCityDialog(
            input = cityInput,
            error = error,
            onInputChange = { cityInput = it; error = null },
            onDismiss = ::dismissAddCity,
            onConfirm = {
                scope.launch {
                    val city = normalizeCity(cityInput)
                    error = when {
                        !isValidCitySlug(city) -> context.getString(R.string.invalid_city)
                        city in cities -> context.getString(R.string.city_already_added)
                        !store.addCity(city) -> context.getString(R.string.invalid_city)
                        else -> {
                            dismissAddCity()
                            WeatherScheduler.refreshNow(context)
                            updateWeatherWidget(context)
                            null
                        }
                    }
                }
            },
        )
    }
}

/** Global Doze setting: the button disappears once the system grants exemption. */
@Composable
internal fun BatteryOptimizationControl(
    ignoring: Boolean,
    onRequest: () -> Unit,
) {
    Text(stringResource(R.string.battery_optimizations_hint))
    if (ignoring) {
        Text(stringResource(R.string.battery_optimizations_ignored))
    } else {
        Button(
            onClick = onRequest,
            modifier = Modifier.testTag(WeatherControlTags.BATTERY_OPTIMIZATIONS),
        ) {
            Text(stringResource(R.string.ignore_battery_optimizations))
        }
    }
}

/** Add-city dialog whose text field is focused as soon as the dialog opens. */
@Composable
internal fun AddCityDialog(
    input: String,
    error: String?,
    onInputChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val attached = remember { mutableStateOf(false) }

    // A dialog's content is laid out after its LaunchedEffects run, so wait for
    // the field to be attached before claiming focus.
    LaunchedEffect(attached.value) {
        if (attached.value) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_city)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChange,
                    label = { Text(stringResource(R.string.city_hint)) },
                    singleLine = true,
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .onGloballyPositioned { attached.value = true },
                )
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.add_city))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

internal object WeatherControlTags {
    const val WIDGET = "widget_switch"
    const val SUN_ALERT = "sun_alert_switch"
    const val RAIN_ALERT = "rain_alert_switch"
    const val SNOW_ALERT = "snow_alert_switch"
    const val SUN_LEAD = "sun_lead_selector"
    const val RAIN_LEAD = "rain_lead_selector"
    const val SNOW_LEAD = "snow_lead_selector"
    const val OPEN_WEBSITE = "open_website_button"
    const val BATTERY_OPTIMIZATIONS = "battery_optimizations_button"
}

/** Dropdown with the supported lead times; each condition uses its own setting. */
@Composable
private fun LeadTimeSelector(label: String, tag: String, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier.testTag(tag),
        ) {
            Text(label)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ALERT_LEAD_OPTIONS.forEach { minutes ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.lead_minutes, minutes)) },
                    onClick = {
                        expanded = false
                        onSelect(minutes)
                    },
                )
            }
        }
    }
}

@Composable
internal fun CityItem(
    label: String,
    index: Int,
    cityCount: Int,
    forecast: CityForecast?,
    detailsExpanded: Boolean,
    hidden: Boolean,
    widgetSelected: Boolean = false,
    sunAlert: Boolean = false,
    rainAlert: Boolean = false,
    snowAlert: Boolean = false,
    sunLeadMinutes: Int = 0,
    rainLeadMinutes: Int = 0,
    snowLeadMinutes: Int = 0,
    nowMillis: Long = 0L,
    onToggleExpanded: () -> Unit,
    onToggleNotificationVisibility: (Boolean) -> Unit,
    onToggleWidget: (Boolean) -> Unit = {},
    onOpenWebsite: () -> Unit = {},
    onToggleSunAlert: (Boolean) -> Unit = {},
    onToggleRainAlert: (Boolean) -> Unit = {},
    onToggleSnowAlert: (Boolean) -> Unit = {},
    onSetSunLeadMinutes: (Int) -> Unit = {},
    onSetRainLeadMinutes: (Int) -> Unit = {},
    onSetSnowLeadMinutes: (Int) -> Unit = {},
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // The city line and colour grid are one tappable summary;
        // no separate show/hide button is needed.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpanded),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall,
                )
                if (hidden) {
                    Icon(
                        painter = painterResource(R.drawable.ic_eye_off),
                        contentDescription = stringResource(R.string.hidden_indicator),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (forecast != null) {
                val storedHours = forecast.hours
                val hours = storedHours.currentOrFutureHours(nowMillis)
                if (hours.isNotEmpty()) {
                    HourlyOverview(label, hours, forecast.sunEvents)
                } else if (storedHours.isNotEmpty()) {
                    StaleHourlyOverview()
                }
                val storedNowcastCells = forecast.nowcastCells
                val nowcastCells = storedNowcastCells.currentOrFutureNowcast(nowMillis)
                val nowcastModifier = Modifier.padding(top = WeatherRowSpacing.beforeNowcast)
                when {
                    nowcastCells.any { it.kind.isRain() || it.kind.isSun() || it.kind.isSnow() } ->
                        NowcastOverview(label, nowcastCells, modifier = nowcastModifier)

                    storedNowcastCells.isNotEmpty() && nowcastCells.isEmpty() ->
                        StaleNowcastRow(modifier = nowcastModifier)
                }
            } else {
                Text(stringResource(R.string.no_forecast))
            }
        }

        // Controls must stay reachable for cities without a forecast: those are
        // exactly the broken ones the user needs to be able to remove.
        if (detailsExpanded) {
            if (forecast != null) {
                UpdatedText(forecast.fetchedAtMillis, nowMillis)
                Text(stringResource(R.string.sun, forecast.sunSummary))
                Text(stringResource(R.string.rain, forecast.rainSummary))
                if (forecast.snowSummary.isNotEmpty()) {
                    Text(stringResource(R.string.snow, forecast.snowSummary))
                }
                val sunrise = forecast.sunriseLabel?.let {
                    stringResource(R.string.sunrise, it)
                }
                val sunset = forecast.sunsetLabel?.let {
                    stringResource(R.string.sunset, it)
                }
                if (sunrise != null || sunset != null) {
                    Text(listOfNotNull(sunrise, sunset).joinToString(" · "))
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Switch(
                    checked = !hidden,
                    onCheckedChange = onToggleNotificationVisibility,
                )
                Text(stringResource(R.string.show_in_notification))
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Switch(
                    checked = widgetSelected,
                    onCheckedChange = onToggleWidget,
                    modifier = Modifier.testTag(WeatherControlTags.WIDGET),
                )
                Text(stringResource(R.string.show_in_widget))
            }

            Text(
                stringResource(R.string.additional_notifications),
                style = MaterialTheme.typography.labelMedium,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Switch(
                    checked = sunAlert,
                    onCheckedChange = onToggleSunAlert,
                    modifier = Modifier.testTag(WeatherControlTags.SUN_ALERT),
                )
                Text(stringResource(R.string.sun_alert))
            }
            if (sunAlert) {
                LeadTimeSelector(
                    label = stringResource(R.string.sun_lead, sunLeadMinutes),
                    tag = WeatherControlTags.SUN_LEAD,
                    onSelect = onSetSunLeadMinutes,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Switch(
                    checked = rainAlert,
                    onCheckedChange = onToggleRainAlert,
                    modifier = Modifier.testTag(WeatherControlTags.RAIN_ALERT),
                )
                Text(stringResource(R.string.rain_alert))
            }
            if (rainAlert) {
                LeadTimeSelector(
                    label = stringResource(R.string.rain_lead, rainLeadMinutes),
                    tag = WeatherControlTags.RAIN_LEAD,
                    onSelect = onSetRainLeadMinutes,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Switch(
                    checked = snowAlert,
                    onCheckedChange = onToggleSnowAlert,
                    modifier = Modifier.testTag(WeatherControlTags.SNOW_ALERT),
                )
                Text(stringResource(R.string.snow_alert))
            }
            if (snowAlert) {
                LeadTimeSelector(
                    label = stringResource(R.string.snow_lead, snowLeadMinutes),
                    tag = WeatherControlTags.SNOW_LEAD,
                    onSelect = onSetSnowLeadMinutes,
                )
            }

            TextButton(
                onClick = onOpenWebsite,
                modifier = Modifier.testTag(WeatherControlTags.OPEN_WEBSITE),
            ) {
                Text(stringResource(R.string.open_on_wetteronline))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    enabled = index > 0,
                    onClick = { onMove(-1) },
                ) {
                    Text("↑ " + stringResource(R.string.move_up))
                }
                TextButton(
                    enabled = index < cityCount - 1,
                    onClick = { onMove(1) },
                ) {
                    Text("↓ " + stringResource(R.string.move_down))
                }
                Button(onClick = onRemove) {
                    Text(stringResource(R.string.remove))
                }
            }
        }
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
