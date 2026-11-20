package de.jayp33.sonnenregen.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.jayp33.sonnenregen.data.WeatherStore
import de.jayp33.sonnenregen.notification.WeatherNotifier
import de.jayp33.sonnenregen.work.WeatherScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        WeatherScheduler.schedule(context)

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val stored = WeatherStore(context).load()
                if (stored != null) WeatherNotifier.show(context, stored)
            } finally {
                pending.finish()
            }
        }
    }
}
