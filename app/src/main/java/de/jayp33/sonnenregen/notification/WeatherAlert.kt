package de.jayp33.sonnenregen.notification

import de.jayp33.sonnenregen.data.CityForecast
import de.jayp33.sonnenregen.domain.TIMEZONE
import de.jayp33.sonnenregen.domain.displayNameFor
import kotlinx.serialization.Serializable

/** Which condition a per-city alert is about. */
enum class AlertKind { SUN, RAIN }

/** One per-city, per-condition alert. */
data class WeatherAlert(
    val city: String,
    val displayName: String,
    val kind: AlertKind,
    /** Configured lead; a positive value makes the notification announce the start. */
    val leadMinutes: Int = 0,
    val eventAtMillis: Long? = null,
    val zoneId: String = TIMEZONE,
)

/** Stable identity for an alert's settings, state, and WorkManager job. */
data class AlertKey(val city: String, val kind: AlertKind) {
    val id: String get() = "$city|${kind.name}"

    val workName: String
        get() = "alert_${city.replace(Regex("[^A-Za-z0-9_-]"), "_")}_${kind.name.lowercase()}"
}

@Serializable
enum class AlertStage { ARMED, SCHEDULED, NOTIFIED }

/**
 * One episode per city/condition. `eventAtMillis` is the onset time currently
 * being tracked; `episodeEnded` flips once the condition was observed as active
 * so the next upcoming event is treated as a new episode.
 */
@Serializable
data class AlertState(
    val stage: AlertStage = AlertStage.ARMED,
    val eventAtMillis: Long? = null,
    val episodeEnded: Boolean = false,
)

data class ScheduledAlert(
    val key: AlertKey,
    val alert: WeatherAlert,
    val notifyAtMillis: Long,
    val eventAtMillis: Long,
)

data class AlertPlan(
    val alerts: List<WeatherAlert>,
    val scheduled: List<ScheduledAlert>,
    val cancelled: Set<AlertKey>,
    val states: Map<String, AlertState>,
)

/**
 * Turns fresh forecasts into alert actions. Alerts are posted at
 * `event − lead` via a one-shot job; if that moment is already past when the
 * forecast arrives, the alert is posted immediately. A single episode never
 * alerts twice, even when its event time shifts in a later forecast.
 */
object AlertPlanner {
    fun plan(
        states: Map<String, AlertState>,
        forecasts: List<CityForecast>,
        sunAlertCities: Set<String>,
        rainAlertCities: Set<String>,
        sunLeadMinutes: Map<String, Int> = emptyMap(),
        rainLeadMinutes: Map<String, Int> = emptyMap(),
        nowMillis: Long = System.currentTimeMillis(),
    ): AlertPlan {
        val byCity = forecasts.associateBy { it.city }
        val nextStates = states.toMutableMap()
        val alerts = mutableListOf<WeatherAlert>()
        val scheduled = mutableListOf<ScheduledAlert>()
        val cancelled = mutableSetOf<AlertKey>()

        fun handle(city: String, kind: AlertKind) {
            val key = AlertKey(city, kind)
            val state = nextStates[key.id] ?: AlertState()
            val enabled = when (kind) {
                AlertKind.SUN -> city in sunAlertCities
                AlertKind.RAIN -> city in rainAlertCities
            }
            if (!enabled) {
                if (state.stage != AlertStage.ARMED) cancelled += key
                if (state != AlertState()) nextStates[key.id] = AlertState()
                return
            }

            val forecast = byCity[city] ?: return // keep the schedule on a failed fetch
            val currently = when (kind) {
                AlertKind.SUN -> forecast.sunCurrently
                AlertKind.RAIN -> forecast.rainCurrently
            }
            val lead = when (kind) {
                AlertKind.SUN -> sunLeadMinutes[city] ?: 0
                AlertKind.RAIN -> rainLeadMinutes[city] ?: 0
            }
            val eventAt = when {
                currently -> null
                kind == AlertKind.SUN -> forecast.sunEventAtMillis
                else -> forecast.rainEventAtMillis
            }
            val alert = WeatherAlert(
                city = city,
                displayName = displayNameFor(city, forecast.displayName),
                kind = kind,
                leadMinutes = lead,
                eventAtMillis = eventAt,
                zoneId = forecast.timezone,
            )

            when {
                currently -> when (state.stage) {
                    // Enabling an alert during an ongoing condition must not fire
                    // retroactively, but it does close that episode.
                    AlertStage.ARMED -> nextStates[key.id] =
                        AlertState(AlertStage.NOTIFIED, nowMillis, episodeEnded = true)

                    AlertStage.SCHEDULED -> {
                        cancelled += key
                        alerts += alert
                        nextStates[key.id] =
                            AlertState(AlertStage.NOTIFIED, nowMillis, episodeEnded = true)
                    }

                    AlertStage.NOTIFIED -> if (!state.episodeEnded) {
                        nextStates[key.id] = state.copy(episodeEnded = true)
                    }
                }

                eventAt == null -> {
                    if (state.stage != AlertStage.ARMED) cancelled += key
                    nextStates[key.id] = AlertState(episodeEnded = state.episodeEnded)
                }

                state.stage == AlertStage.NOTIFIED && !state.episodeEnded -> Unit

                else -> {
                    // Always re-enqueue with REPLACE: it picks up shifted event
                    // times and changed lead times without extra state.
                    if (state.stage == AlertStage.SCHEDULED) cancelled += key
                    val notifyAt = eventAt - lead * 60_000L
                    if (notifyAt <= nowMillis && state.stage != AlertStage.SCHEDULED) {
                        // First time we see an already-due event: post now instead
                        // of queueing a job that would be late.
                        alerts += alert
                        nextStates[key.id] = AlertState(AlertStage.NOTIFIED, eventAt)
                    } else {
                        // An existing schedule may have fired between the worker
                        // reading state and writing it back. Re-enqueue with the
                        // remaining delay instead of posting a duplicate.
                        scheduled += ScheduledAlert(key, alert, notifyAt, eventAt)
                        nextStates[key.id] = AlertState(AlertStage.SCHEDULED, eventAt)
                    }
                }
            }
        }

        (byCity.keys + sunAlertCities + rainAlertCities).distinct().forEach { city ->
            handle(city, AlertKind.SUN)
            handle(city, AlertKind.RAIN)
        }

        return AlertPlan(alerts, scheduled, cancelled, nextStates)
    }
}
