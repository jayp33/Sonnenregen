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
import de.jayp33.sonnenregen.widget.updateWeatherWidget
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Provider-format and input errors are permanent; transport errors can be retried. */
internal fun isRetryableFailure(exc: Exception): Boolean = exc !is ParseException && exc !is IllegalArgumentException

class WeatherWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    private val client = WetterOnlineClient()
    private val store = WeatherStore(applicationContext)
    private val notifier = WeatherNotifier
    private val fetchLimit = Semaphore(MAX_CONCURRENT_FETCHES)

    override suspend fun doWork(): Result {
        if (!store.isEnabled()) return Result.success()

        val cities = store.cities()
        val outcomes = coroutineScope {
            cities.associateWith { city -> async { fetchLimit.withPermit { fetch(city) } } }
                .mapValues { (_, deferred) -> deferred.await() }
        }

        val successful = outcomes.values.filterIsInstance<Outcome.Success>().map { it.forecast }
        store.saveForecasts(successful)

        val alertStates = store.alertStates()
        val plan = AlertPlanner.plan(
            states = alertStates,
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
        store.saveAlertStates(alertStates, plan.states)

        // Failed cities keep their last good forecast, shown as stale.
        val staleCities = outcomes.filterValues { it is Outcome.Failure }.keys
        notifier.show(applicationContext, store.statuses(staleCities))
        updateWeatherWidget(applicationContext)

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
        Log.w(TAG, "Parse failed; markup may have changed (${exc.javaClass.simpleName})")
        Outcome.Failure(retryable = isRetryableFailure(exc))
    } catch (exc: IllegalArgumentException) {
        Log.w(TAG, "Invalid city (${exc.javaClass.simpleName})")
        Outcome.Failure(retryable = isRetryableFailure(exc))
    } catch (exc: SourceException) {
        // Exception messages carry provider URLs, which contain the city slug.
        Log.w(TAG, "Fetch failed; retrying (${exc.javaClass.simpleName})")
        Outcome.Failure(retryable = isRetryableFailure(exc))
    } catch (exc: Exception) {
        Log.w(TAG, "Unexpected error; retrying (${exc.javaClass.simpleName})")
        Outcome.Failure(retryable = isRetryableFailure(exc))
    }

    private sealed interface Outcome {
        data class Success(val forecast: CityForecast) : Outcome
        data class Failure(val retryable: Boolean) : Outcome
    }

    private companion object {
        const val TAG = "WeatherWorker"
        const val MAX_CONCURRENT_FETCHES = 2
    }
}
