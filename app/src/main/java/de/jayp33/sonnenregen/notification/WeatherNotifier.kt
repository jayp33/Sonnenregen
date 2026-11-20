package de.jayp33.sonnenregen.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import de.jayp33.sonnenregen.R
import de.jayp33.sonnenregen.data.CityStatus
import de.jayp33.sonnenregen.receiver.RefreshReceiver
import de.jayp33.sonnenregen.ui.MainActivity

object WeatherNotifier {
    private const val CHANNEL_ID = "weather"
    private const val CATEGORY = "weather"
    private const val NOTIFICATION_ID = 1001

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notification_channel_description)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** One ongoing notification; one city uses the city as header, many use an inbox. */
    fun show(context: Context, statuses: List<CityStatus>) {
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

        val openIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            2,
            openIntent,
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
            .addAction(R.drawable.ic_stat_sun, context.getString(R.string.refresh), refreshPendingIntent)
            .addAction(R.drawable.ic_stat_sun, context.getString(R.string.open_app), openPendingIntent)

        if (view.useInbox) {
            val inboxStyle = NotificationCompat.InboxStyle()
            view.lines.forEach(inboxStyle::addLine)
            builder.setStyle(inboxStyle)
        }

        val notification = builder.build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (exc: SecurityException) {
            // POST_NOTIFICATIONS was denied on Android 13+; keep the app usable.
            Log.w(TAG, "Cannot post notification", exc)
        }
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private const val TAG = "WeatherNotifier"
}
