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
        val eventAtMillis = inputData.getLong(KEY_EVENT_AT, -1L).takeIf { it > 0L }
        val leadMinutes = inputData.getInt(KEY_LEAD_MINUTES, 0)
        val zoneId = inputData.getString(KEY_ZONE_ID).orEmpty()

        val store = WeatherStore(applicationContext)
        if (!store.isEnabled()) return Result.success()

        // A stale job can survive a refresh or a settings change. Only the
        // scheduled episode it was enqueued for may actually be posted.
        val key = AlertKey(city, kind)
        val state = store.alertStates()[key.id] ?: return Result.success()
        if (state.stage != AlertStage.SCHEDULED ||
            (eventAtMillis != null && state.eventAtMillis != eventAtMillis)
        ) {
            return Result.success()
        }

        WeatherNotifier.showAlerts(
            applicationContext,
            listOf(
                WeatherAlert(
                    city = city,
                    displayName = displayName,
                    kind = kind,
                    leadMinutes = leadMinutes,
                    eventAtMillis = eventAtMillis,
                    zoneId = zoneId,
                ),
            ),
        )
        store.markAlertNotified(key, eventAtMillis ?: -1L)
        return Result.success()
    }

    internal companion object {
        const val KEY_CITY = "alert_city"
        const val KEY_KIND = "alert_kind"
        const val KEY_DISPLAY_NAME = "alert_display_name"
        const val KEY_EVENT_AT = "alert_event_at"
        const val KEY_LEAD_MINUTES = "alert_lead_minutes"
        const val KEY_ZONE_ID = "alert_zone_id"
    }
}
