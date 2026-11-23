package de.jayp33.sonnenregen

import android.app.Application
import android.util.Log
import de.jayp33.sonnenregen.data.WeatherStore
import de.jayp33.sonnenregen.notification.WeatherNotifier
import de.jayp33.sonnenregen.work.WeatherScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SonnenregenApp : Application() {
    override fun onCreate() {
        super.onCreate()
        WeatherNotifier.createChannel(this)

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (WeatherStore(this@SonnenregenApp).isEnabled()) {
                    WeatherScheduler.schedule(this@SonnenregenApp)
                }
            } catch (exc: Exception) {
                Log.w(TAG, "Could not restore weather schedule", exc)
            }
        }
    }

    private companion object {
        const val TAG = "SonnenregenApp"
    }
}
