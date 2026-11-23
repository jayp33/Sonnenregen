package de.jayp33.sonnenregen.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import de.jayp33.sonnenregen.data.CityForecast
import de.jayp33.sonnenregen.data.ParseException
import de.jayp33.sonnenregen.data.SourceException
import de.jayp33.sonnenregen.data.WeatherStore
import de.jayp33.sonnenregen.data.WetterOnlineClient
import de.jayp33.sonnenregen.domain.ForecastAnalyzer
import de.jayp33.sonnenregen.notification.AlertPlanner
import de.jayp33.sonnenregen.notification.WeatherNotifier
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class WeatherWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    private val client = WetterOnlineClient()
    private val store = WeatherStore(applicationContext)
    private val notifier = WeatherNotifier

    override suspend fun doWork(): Result {
        if (!store.isEnabled()) return Result.success()

        val cities = store.cities()
        val outcomes = coroutineScope {
            cities.associateWith { city -> async { fetch(city) } }
                .mapValues { (_, deferred) -> deferred.await() }
        }

        val successful = outcomes.values.filterIsInstance<Outcome.Success>().map { it.forecast }
        store.saveForecasts(successful)

        val plan = AlertPlanner.plan(
            states = store.alertStates(),
            forecasts = successful,
            sunAlertCities = store.sunAlertCities(),
            rainAlertCities = store.rainAlertCities(),
            snowAlertCities = store.snowAlertCities(),
            sunLeadMinutes = store.sunLeadMinutes(),
            rainLeadMinutes = store.rainLeadMinutes(),
            snowLeadMinutes = store.snowLeadMinutes(),
        )
        plan.cancelled.forEach { WeatherScheduler.cancelAlert(applicationContext, it) }
        plan.scheduled.forEach { WeatherScheduler.scheduleAlert(applicationContext, it) }
        notifier.showAlerts(applicationContext, plan.alerts)
        store.saveAlertStates(plan.states)

        // Failed cities keep their last good forecast, shown as stale.
        val staleCities = outcomes.filterValues { it is Outcome.Failure }.keys
        notifier.show(applicationContext, store.statuses(staleCities))

        return when {
            cities.isEmpty() -> Result.success()
            successful.isNotEmpty() -> Result.success()
            outcomes.values.any { it is Outcome.Failure && it.retryable } -> Result.retry()
            else -> Result.failure()
        }
    }

    private suspend fun fetch(city: String): Outcome = try {
        val (hourlyHtml, nowcastHtml) = client.fetch(city)
        val forecast = ForecastAnalyzer.analyze(city, hourlyHtml, nowcastHtml)
        Outcome.Success(store.format(forecast))
    } catch (exc: ParseException) {
        Log.w(TAG, "Parse failed for $city; markup may have changed", exc)
        Outcome.Failure(retryable = false)
    } catch (exc: IllegalArgumentException) {
        Log.w(TAG, "Invalid city: $city", exc)
        Outcome.Failure(retryable = false)
    } catch (exc: SourceException) {
        Log.w(TAG, "Fetch failed for $city; retrying", exc)
        Outcome.Failure(retryable = true)
    } catch (exc: Exception) {
        Log.w(TAG, "Unexpected error for $city; retrying", exc)
        Outcome.Failure(retryable = true)
    }

    private sealed interface Outcome {
        data class Success(val forecast: CityForecast) : Outcome
        data class Failure(val retryable: Boolean) : Outcome
    }

    private companion object {
        const val TAG = "WeatherWorker"
    }
}
