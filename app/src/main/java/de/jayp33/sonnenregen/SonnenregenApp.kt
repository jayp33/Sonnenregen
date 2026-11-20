package de.jayp33.sonnenregen

import android.app.Application
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
            if (WeatherStore(this@SonnenregenApp).isEnabled()) {
                WeatherScheduler.schedule(this@SonnenregenApp)
            }
        }
    }
}
