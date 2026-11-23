package de.jayp33.sonnenregen.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import de.jayp33.sonnenregen.notification.AlertKey
import de.jayp33.sonnenregen.notification.AlertWorker
import de.jayp33.sonnenregen.notification.ScheduledAlert
import java.util.concurrent.TimeUnit

object WeatherScheduler {
    private const val PERIODIC_NAME = "weather_periodic"
    private const val ONCE_NAME = "weather_once"

    private fun constraints() = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<WeatherWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints())
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun refreshNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<WeatherWorker>()
            .setConstraints(constraints())
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            ONCE_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    /** Post an alert at its calculated time, independent of the 15-minute poll. */
    fun scheduleAlert(context: Context, scheduled: ScheduledAlert) {
        val input = workDataOf(
            AlertWorker.KEY_CITY to scheduled.key.city,
            AlertWorker.KEY_KIND to scheduled.key.kind.name,
            AlertWorker.KEY_DISPLAY_NAME to scheduled.alert.displayName,
            AlertWorker.KEY_EVENT_AT to scheduled.eventAtMillis,
        )
        val delayMillis = maxOf(0L, scheduled.notifyAtMillis - System.currentTimeMillis())
        val request = OneTimeWorkRequestBuilder<AlertWorker>()
            .setInputData(input)
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            scheduled.key.workName,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun cancelAlert(context: Context, key: AlertKey) {
        WorkManager.getInstance(context).cancelUniqueWork(key.workName)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_NAME)
    }
}
