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
import de.jayp33.sonnenregen.domain.DEFAULT_CITY
import java.util.concurrent.TimeUnit

object WeatherScheduler {
    const val KEY_CITY = "city"

    private const val PERIODIC_NAME = "weather_periodic"
    private const val ONCE_NAME = "weather_once"

    private fun constraints() = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<WeatherWorker>(15, TimeUnit.MINUTES)
            .setInputData(workDataOf(KEY_CITY to DEFAULT_CITY))
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
            .setInputData(workDataOf(KEY_CITY to DEFAULT_CITY))
            .setConstraints(constraints())
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            ONCE_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_NAME)
    }
}
