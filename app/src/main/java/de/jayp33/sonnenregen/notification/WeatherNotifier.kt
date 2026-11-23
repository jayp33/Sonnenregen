package de.jayp33.sonnenregen.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import de.jayp33.sonnenregen.R
import de.jayp33.sonnenregen.data.CityStatus
import de.jayp33.sonnenregen.domain.CLOCK_TIME
import de.jayp33.sonnenregen.domain.TIMEZONE
import de.jayp33.sonnenregen.receiver.RefreshReceiver
import de.jayp33.sonnenregen.ui.MainActivity
import java.time.Instant
import java.time.ZoneId

object WeatherNotifier {
    private const val CHANNEL_ID = "weather"
    private const val ALERT_CHANNEL_ID = "weather_alerts"
    private const val CATEGORY = "weather"
    private const val NOTIFICATION_ID = 1001
    private const val ALERT_NOTIFICATION_ID = 2000

    fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.notification_channel_description)
            },
        )
        // Alerts need their own channel so users can control them independently
        // of the low-priority persistent summary.
        manager.createNotificationChannel(
            NotificationChannel(
                ALERT_CHANNEL_ID,
                context.getString(R.string.notification_alert_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.notification_alert_channel_description)
            },
        )
    }

    /** One ongoing notification; both single and multi-city bodies wrap in big text. */
    fun show(context: Context, statuses: List<CityStatus>) {
        val notification = buildNotification(context, statuses)
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (exc: SecurityException) {
            // POST_NOTIFICATIONS was denied on Android 13+; keep the app usable.
            Log.w(TAG, "Cannot post notification", exc)
        }
    }

    /**
     * Post one independent, auto-cancelling alert per city/condition.
     * Returns false if any post was denied so callers can retry the episode.
     */
    fun showAlerts(context: Context, alerts: List<WeatherAlert>): Boolean {
        val manager = NotificationManagerCompat.from(context)
        var allPosted = true
        alerts.forEach { alert ->
            try {
                manager.notify(alertNotificationId(alert), buildAlertNotification(context, alert))
            } catch (exc: SecurityException) {
                allPosted = false
                Log.w(TAG, "Cannot post alert for ${alert.city}", exc)
            }
        }
        return allPosted
    }

    internal fun alertNotificationId(alert: WeatherAlert): Int =
        ALERT_NOTIFICATION_ID + (alert.city + alert.kind.name).hashCode() and 0x7FFF

    /** Separate from posting so alert content and IDs can be tested on a device. */
    internal fun buildAlertNotification(
        context: Context,
        alert: WeatherAlert,
        nowMillis: Long = System.currentTimeMillis(),
    ): Notification {
        val text = alertText(context, alert, nowMillis)
        val openPendingIntent = PendingIntent.getActivity(
            context,
            ALERT_OPEN_REQUEST_CODE,
            openAppIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_sun)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(CATEGORY)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openPendingIntent)
            .build()
    }

    /**
     * With a lead time, announce the upcoming start (remaining minutes plus the
     * local clock time) instead of claiming the condition is already here.
     */
    private fun alertText(context: Context, alert: WeatherAlert, nowMillis: Long): String {
        val eventAt = alert.eventAtMillis?.takeIf { it > 0L }
        if (alert.leadMinutes > 0 && eventAt != null) {
            val remaining = ((eventAt - nowMillis + 30_000L) / 60_000L)
                .toInt().coerceAtLeast(0)
            val zone = runCatching { ZoneId.of(alert.zoneId.ifBlank { TIMEZONE }) }
                .getOrDefault(ZoneId.of(TIMEZONE))
            val time = Instant.ofEpochMilli(eventAt).atZone(zone).toLocalTime().format(CLOCK_TIME)
            return when (alert.kind) {
                AlertKind.SUN -> context.getString(
                    R.string.alert_sun_in,
                    alert.displayName,
                    remaining,
                    time,
                )
                AlertKind.RAIN -> context.getString(
                    R.string.alert_rain_in,
                    alert.displayName,
                    remaining,
                    time,
                )
                AlertKind.SNOW -> context.getString(
                    R.string.alert_snow_in,
                    alert.displayName,
                    remaining,
                    time,
                )
            }
        }
        return when (alert.kind) {
            AlertKind.SUN -> context.getString(R.string.alert_sun, alert.displayName)
            AlertKind.RAIN -> context.getString(R.string.alert_rain, alert.displayName)
            AlertKind.SNOW -> context.getString(R.string.alert_snow, alert.displayName)
        }
    }

    /** Also used by tests to verify the notification's content intent. */
    internal fun openAppIntent(context: Context): Intent =
        Intent(context, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
        }

    /** Separate from posting so the content intent can be tested on a device. */
    internal fun buildNotification(context: Context, statuses: List<CityStatus>): Notification {
        val view = NotificationViewBuilder.build(
            statuses = statuses,
            appName = context.getString(R.string.app_name),
            noCitiesText = context.getString(R.string.no_cities_notification),
            textFor = {
                if (it.stale) context.getString(R.string.weather_unavailable) else it.compactSummary
            },
            lineFor = { city, text -> context.getString(R.string.city_line, city, text) },
        )

        val pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        val refreshIntent = Intent(context, RefreshReceiver::class.java).apply {
            action = RefreshReceiver.ACTION_REFRESH
        }
        val refreshPendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            refreshIntent,
            pendingIntentFlags,
        )

        val openPendingIntent = PendingIntent.getActivity(
            context,
            OPEN_REQUEST_CODE,
            openAppIntent(context),
            pendingIntentFlags,
        )

        // The oldest real timestamp determines how fresh this notification is;
        // cities without a forecast have none and must not drag it to 1970.
        val fetchedAt = statuses.filter { it.fetchedAtMillis > 0 }
            .minOfOrNull { it.fetchedAtMillis } ?: System.currentTimeMillis()
        val anyFresh = statuses.any { !it.stale }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_sun)
            .setContentTitle(view.title)
            .setContentText(view.text)
            .setSubText(
                context.getString(
                    if (anyFresh) R.string.updated else R.string.weather_unavailable,
                ),
            )
            .setWhen(fetchedAt)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setOngoing(true)
            .setCategory(CATEGORY)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openPendingIntent)
            .addAction(R.drawable.ic_stat_sun, context.getString(R.string.refresh), refreshPendingIntent)

        if (view.useBigText) {
            // InboxStyle ellipsized every line: a long city name swallowed the
            // summary, and a long single-city summary lost its tail. BigTextStyle
            // wraps instead. Multi-city lines are already newline-separated.
            val bigText = view.lines.joinToString("\n").ifEmpty { view.text }
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
        }

        return builder.build()
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    internal const val OPEN_REQUEST_CODE = 2
    internal const val ALERT_OPEN_REQUEST_CODE = 3
    private const val TAG = "WeatherNotifier"
}
