package de.jayp33.sonnenregen.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import de.jayp33.sonnenregen.data.SourceException
import de.jayp33.sonnenregen.data.ParseException
import de.jayp33.sonnenregen.data.WeatherStore
import de.jayp33.sonnenregen.data.WetterOnlineClient
import de.jayp33.sonnenregen.domain.DEFAULT_CITY
import de.jayp33.sonnenregen.domain.ForecastAnalyzer
import de.jayp33.sonnenregen.notification.WeatherNotifier

class WeatherWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    private val client = WetterOnlineClient()
    private val store = WeatherStore(applicationContext)
    private val notifier = WeatherNotifier

    override suspend fun doWork(): Result {
        if (!store.isEnabled()) return Result.success()

        val city = inputData.getString(WeatherScheduler.KEY_CITY) ?: DEFAULT_CITY
        return try {
            val (hourlyHtml, nowcastHtml) = client.fetch(city)
            val forecast = ForecastAnalyzer.analyze(city, hourlyHtml, nowcastHtml)

            val stored = store.save(forecast)
            notifier.show(applicationContext, stored)
            Result.success()
        } catch (exc: IllegalArgumentException) {
            Log.w(TAG, "Invalid city: $city", exc)
            showLastGoodResult()
            Result.failure()
        } catch (exc: ParseException) {
            Log.w(TAG, "WetterOnline markup may have changed", exc)
            showLastGoodResult()
            Result.failure()
        } catch (exc: SourceException) {
            Log.w(TAG, "Fetch failed, retrying", exc)
            showLastGoodResult()
            Result.retry()
        } catch (exc: Exception) {
            Log.w(TAG, "Unexpected error, retrying", exc)
            showLastGoodResult()
            Result.retry()
        }
    }

    private suspend fun showLastGoodResult() {
        val stored = store.load() ?: return
        notifier.show(applicationContext, stored, stale = true)
    }

    private companion object {
        const val TAG = "WeatherWorker"
    }
}
