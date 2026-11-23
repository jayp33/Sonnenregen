package de.jayp33.sonnenregen

import de.jayp33.sonnenregen.data.CityForecast
import de.jayp33.sonnenregen.notification.AlertKey
import de.jayp33.sonnenregen.notification.AlertKind
import de.jayp33.sonnenregen.notification.AlertPlanner
import de.jayp33.sonnenregen.notification.AlertStage
import de.jayp33.sonnenregen.notification.AlertState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertPlannerTest {
    private val now = 1_000_000_000_000L
    private val minute = 60_000L
    private val sunKey = AlertKey("berlin", AlertKind.SUN).id
    private val snowKey = AlertKey("berlin", AlertKind.SNOW).id

    @Test
    fun schedules_a_fresh_event_at_event_minus_lead() {
        val event = now + 60 * minute
        val plan = plan(
            forecasts = listOf(forecast(sunEventAt = event)),
            sunLead = 30,
        )

        assertEquals(1, plan.scheduled.size)
        assertEquals(now + 30 * minute, plan.scheduled.single().notifyAtMillis)
        assertEquals(event, plan.scheduled.single().eventAtMillis)
        assertEquals(30, plan.scheduled.single().alert.leadMinutes)
        assertEquals(event, plan.scheduled.single().alert.eventAtMillis)
        assertEquals("Europe/Berlin", plan.scheduled.single().alert.zoneId)
        assertEquals(AlertStage.SCHEDULED, plan.states[sunKey]?.stage)
        assertTrue(plan.alerts.isEmpty())
    }

    @Test
    fun zero_lead_schedules_exactly_at_the_event() {
        val event = now + 45 * minute
        val plan = plan(forecasts = listOf(forecast(sunEventAt = event)), sunLead = 0)

        assertEquals(event, plan.scheduled.single().notifyAtMillis)
    }

    @Test
    fun a_due_event_alerts_immediately_without_scheduling() {
        val plan = plan(
            forecasts = listOf(forecast(sunEventAt = now + 5 * minute)),
            sunLead = 30,
        )

        assertEquals(listOf(AlertKind.SUN), plan.alerts.map { it.kind })
        assertEquals("Berlin", plan.alerts.single().displayName)
        assertTrue(plan.scheduled.isEmpty())
        assertEquals(AlertStage.NOTIFIED, plan.states[sunKey]?.stage)
    }

    @Test
    fun a_shifted_event_while_notified_does_not_alert_again() {
        val previousEvent = now + 40 * minute
        val plan = plan(
            states = mapOf(sunKey to AlertState(AlertStage.NOTIFIED, previousEvent)),
            forecasts = listOf(forecast(sunEventAt = previousEvent + 30 * minute)),
            sunLead = 15,
        )

        assertTrue(plan.alerts.isEmpty())
        assertTrue(plan.scheduled.isEmpty())
        assertTrue(plan.cancelled.isEmpty())
    }

    @Test
    fun an_unchanged_event_while_notified_does_not_alert_again() {
        val event = now + 40 * minute
        val plan = plan(
            states = mapOf(sunKey to AlertState(AlertStage.NOTIFIED, event)),
            forecasts = listOf(forecast(sunEventAt = event)),
            sunLead = 15,
        )

        assertTrue(plan.alerts.isEmpty())
        assertTrue(plan.scheduled.isEmpty())
    }

    @Test
    fun a_shifted_event_while_scheduled_is_rescheduled_without_an_alert() {
        val oldEvent = now + 60 * minute
        val newEvent = now + 90 * minute
        val plan = plan(
            states = mapOf(sunKey to AlertState(AlertStage.SCHEDULED, oldEvent)),
            forecasts = listOf(forecast(sunEventAt = newEvent)),
            sunLead = 15,
        )

        assertTrue(plan.alerts.isEmpty())
        assertEquals(setOf(AlertKey("berlin", AlertKind.SUN)), plan.cancelled)
        assertEquals(newEvent - 15 * minute, plan.scheduled.single().notifyAtMillis)
        assertEquals(newEvent, plan.states[sunKey]?.eventAtMillis)
    }

    @Test
    fun an_overdue_schedule_is_re_enqueued_without_a_duplicate_alert() {
        val event = now + 5 * minute
        val plan = plan(
            states = mapOf(sunKey to AlertState(AlertStage.SCHEDULED, event)),
            forecasts = listOf(forecast(sunEventAt = event)),
            sunLead = 30,
        )

        assertTrue(plan.alerts.isEmpty())
        assertEquals(1, plan.scheduled.size)
        assertEquals(event - 30 * minute, plan.scheduled.single().notifyAtMillis)
        assertEquals(AlertStage.SCHEDULED, plan.states[sunKey]?.stage)
    }

    @Test
    fun an_early_onset_cancels_the_job_and_alerts_immediately() {
        val plan = plan(
            states = mapOf(sunKey to AlertState(AlertStage.SCHEDULED, now + 60 * minute)),
            forecasts = listOf(forecast(sunCurrently = true)),
            sunLead = 30,
        )

        assertEquals(listOf(AlertKind.SUN), plan.alerts.map { it.kind })
        assertEquals(setOf(AlertKey("berlin", AlertKind.SUN)), plan.cancelled)
        assertEquals(AlertStage.NOTIFIED, plan.states[sunKey]?.stage)
        assertEquals(true, plan.states[sunKey]?.episodeEnded)
    }

    @Test
    fun enabling_during_an_ongoing_condition_closes_the_episode_without_alerting() {
        val plan = plan(forecasts = listOf(forecast(sunCurrently = true)))

        assertTrue(plan.alerts.isEmpty())
        assertEquals(AlertStage.NOTIFIED, plan.states[sunKey]?.stage)
        assertEquals(true, plan.states[sunKey]?.episodeEnded)
    }

    @Test
    fun a_new_episode_after_the_previous_one_ended_is_scheduled() {
        val plan = plan(
            states = mapOf(sunKey to AlertState(AlertStage.NOTIFIED, now - 60 * minute, true)),
            forecasts = listOf(forecast(sunEventAt = now + 60 * minute)),
            sunLead = 15,
        )

        assertEquals(1, plan.scheduled.size)
        assertTrue(plan.alerts.isEmpty())
        assertEquals(AlertStage.SCHEDULED, plan.states[sunKey]?.stage)
    }

    @Test
    fun disabling_an_alert_clears_its_state_and_cancels_the_job() {
        val plan = plan(
            states = mapOf(sunKey to AlertState(AlertStage.SCHEDULED, now + 60 * minute)),
            forecasts = listOf(forecast(sunEventAt = now + 60 * minute)),
            sunAlertCities = emptySet(),
        )

        assertEquals(setOf(AlertKey("berlin", AlertKind.SUN)), plan.cancelled)
        assertEquals(AlertState(), plan.states[sunKey])
    }

    @Test
    fun a_failed_fetch_keeps_an_existing_schedule() {
        val state = AlertState(AlertStage.SCHEDULED, now + 60 * minute)
        val plan = plan(states = mapOf(sunKey to state), forecasts = emptyList())

        assertTrue(plan.alerts.isEmpty())
        assertTrue(plan.scheduled.isEmpty())
        assertTrue(plan.cancelled.isEmpty())
        assertEquals(state, plan.states[sunKey])
    }

    @Test
    fun sun_and_rain_keep_independent_lead_times() {
        val event = now + 60 * minute
        val plan = plan(
            forecasts = listOf(
                forecast(sunEventAt = event, rainEventAt = event).copy(displayName = "Mitte"),
            ),
            rainAlertCities = setOf("berlin"),
            sunLead = 15,
            rainLead = 45,
        )

        assertEquals(2, plan.scheduled.size)
        assertEquals(
            mapOf(
                AlertKind.SUN to event - 15 * minute,
                AlertKind.RAIN to event - 45 * minute,
            ),
            plan.scheduled.associate { it.key.kind to it.notifyAtMillis },
        )
        assertEquals("Mitte", plan.scheduled.first().alert.displayName)
    }

    @Test
    fun snow_alert_uses_its_own_event_time_and_lead() {
        val event = now + 90 * minute
        val plan = plan(
            forecasts = listOf(forecast(snowEventAt = event)),
            sunAlertCities = emptySet(),
            snowAlertCities = setOf("berlin"),
            snowLead = 30,
        )

        assertEquals(1, plan.scheduled.size)
        val scheduled = plan.scheduled.single()
        assertEquals(AlertKind.SNOW, scheduled.key.kind)
        assertEquals(event - 30 * minute, scheduled.notifyAtMillis)
        assertEquals(event, scheduled.alert.eventAtMillis)
        assertEquals(AlertStage.SCHEDULED, plan.states[AlertKey("berlin", AlertKind.SNOW).id]?.stage)
        assertTrue(plan.alerts.isEmpty())
    }

    @Test
    fun rain_and_snow_alerts_are_independent_for_the_same_city() {
        // A city can have rain and snow in the same forecast; neither suppresses
        // the other, and each keeps its own episode state and lead time.
        val rainEvent = now + 60 * minute
        val snowEvent = now + 180 * minute
        val plan = plan(
            forecasts = listOf(forecast(rainEventAt = rainEvent, snowEventAt = snowEvent)),
            rainAlertCities = setOf("berlin"),
            snowAlertCities = setOf("berlin"),
            rainLead = 15,
            snowLead = 45,
        )

        assertEquals(
            mapOf(
                AlertKind.RAIN to rainEvent - 15 * minute,
                AlertKind.SNOW to snowEvent - 45 * minute,
            ),
            plan.scheduled.associate { it.key.kind to it.notifyAtMillis },
        )
        assertEquals(
            setOf(AlertKey("berlin", AlertKind.RAIN), AlertKey("berlin", AlertKind.SNOW)),
            plan.scheduled.map { it.key }.toSet(),
        )
        assertTrue(plan.cancelled.isEmpty())
        assertTrue(plan.alerts.isEmpty())
    }

    @Test
    fun ongoing_snow_closes_the_episode_without_alerting() {
        val plan = plan(
            forecasts = listOf(forecast(snowCurrently = true)),
            snowAlertCities = setOf("berlin"),
        )

        assertTrue(plan.alerts.isEmpty())
        assertTrue(plan.cancelled.isEmpty())
        assertEquals(AlertStage.NOTIFIED, plan.states[snowKey]?.stage)
        assertEquals(true, plan.states[snowKey]?.episodeEnded)
    }

    private fun plan(
        states: Map<String, AlertState> = emptyMap(),
        forecasts: List<CityForecast> = emptyList(),
        sunAlertCities: Set<String> = setOf("berlin"),
        rainAlertCities: Set<String> = emptySet(),
        snowAlertCities: Set<String> = emptySet(),
        sunLead: Int = 0,
        rainLead: Int = 0,
        snowLead: Int = 0,
    ) = AlertPlanner.plan(
        states = states,
        forecasts = forecasts,
        sunAlertCities = sunAlertCities,
        rainAlertCities = rainAlertCities,
        snowAlertCities = snowAlertCities,
        sunLeadMinutes = if (sunLead == 0) emptyMap() else mapOf("berlin" to sunLead),
        rainLeadMinutes = if (rainLead == 0) emptyMap() else mapOf("berlin" to rainLead),
        snowLeadMinutes = if (snowLead == 0) emptyMap() else mapOf("berlin" to snowLead),
        nowMillis = now,
    )

    private fun forecast(
        sunCurrently: Boolean = false,
        rainCurrently: Boolean = false,
        snowCurrently: Boolean = false,
        sunEventAt: Long? = null,
        rainEventAt: Long? = null,
        snowEventAt: Long? = null,
    ) = CityForecast(
        city = "berlin",
        compactSummary = "",
        sunSummary = "",
        rainSummary = "",
        fetchedAtMillis = now,
        displayName = "",
        sunCurrently = sunCurrently,
        rainCurrently = rainCurrently,
        snowCurrently = snowCurrently,
        sunEventAtMillis = sunEventAt,
        rainEventAtMillis = rainEventAt,
        snowEventAtMillis = snowEventAt,
    )
}
