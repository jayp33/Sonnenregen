package de.jayp33.sonnenregen.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import de.jayp33.sonnenregen.data.WeatherStore
import de.jayp33.sonnenregen.domain.displayCity

/** One-shot worker that posts a scheduled sun/rain alert at `event − lead`. */
class AlertWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val city = inputData.getString(KEY_CITY) ?: return Result.failure()
        val kind = runCatching {
            AlertKind.valueOf(inputData.getString(KEY_KIND).orEmpty())
        }.getOrNull() ?: return Result.failure()
        val displayName = inputData.getString(KEY_DISPLAY_NAME) ?: displayCity(city)
        val eventAtMillis = inputData.getLong(KEY_EVENT_AT, -1L)

        val store = WeatherStore(applicationContext)
        if (!store.isEnabled()) return Result.success()

        WeatherNotifier.showAlerts(
            applicationContext,
            listOf(WeatherAlert(city, displayName, kind)),
        )
        store.markAlertNotified(AlertKey(city, kind), eventAtMillis)
        return Result.success()
    }

    internal companion object {
        const val KEY_CITY = "alert_city"
        const val KEY_KIND = "alert_kind"
        const val KEY_DISPLAY_NAME = "alert_display_name"
        const val KEY_EVENT_AT = "alert_event_at"
    }
}
