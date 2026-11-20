package de.jayp33.sonnenregen.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.jayp33.sonnenregen.work.WeatherScheduler

class RefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH) {
            WeatherScheduler.refreshNow(context)
        }
    }

    companion object {
        const val ACTION_REFRESH = "de.jayp33.sonnenregen.REFRESH"
    }
}
