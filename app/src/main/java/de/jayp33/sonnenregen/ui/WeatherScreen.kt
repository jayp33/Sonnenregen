package de.jayp33.sonnenregen.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.jayp33.sonnenregen.R
import de.jayp33.sonnenregen.data.StoredWeather
import de.jayp33.sonnenregen.data.WeatherStore
import de.jayp33.sonnenregen.domain.DEFAULT_CITY
import de.jayp33.sonnenregen.notification.WeatherNotifier
import de.jayp33.sonnenregen.work.WeatherScheduler
import kotlinx.coroutines.launch

@Composable
fun WeatherScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember { WeatherStore(context) }
    val scope = rememberCoroutineScope()

    var stored by remember { mutableStateOf<StoredWeather?>(null) }
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
        stored = store.load()
        enabled = store.isEnabled()
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(stringResource(R.string.city, stored?.city ?: DEFAULT_CITY))

            if (stored != null) {
                Text(stringResource(R.string.sun, stored!!.sunSummary))
                Text(stringResource(R.string.rain, stored!!.rainSummary))
            } else {
                Text(stringResource(R.string.no_forecast))
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
