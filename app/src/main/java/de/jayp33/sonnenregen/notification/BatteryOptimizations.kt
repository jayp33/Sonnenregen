package de.jayp33.sonnenregen.notification

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/** System Doze exemption for this app, so scheduled refreshes and alerts are not delayed. */
object BatteryOptimizations {
    /** True when the system already exempts the app from battery optimizations. */
    fun isIgnoring(context: Context): Boolean {
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return power.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Direct request dialog for this package. Launch it with an Activity Result
     * launcher so [isIgnoring] can be read again when the user returns.
     */
    fun requestIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse(packageUri(context.packageName)))

    /** Fallback for devices that do not offer the direct request dialog. */
    fun settingsIntent(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    internal fun packageUri(packageName: String): String = "package:$packageName"
}
