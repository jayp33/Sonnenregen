package de.jayp33.sonnenregen

import de.jayp33.sonnenregen.data.ParseException
import de.jayp33.sonnenregen.data.SourceException
import de.jayp33.sonnenregen.data.WetterOnlineClient
import de.jayp33.sonnenregen.notification.AlertStage
import de.jayp33.sonnenregen.notification.AlertState
import de.jayp33.sonnenregen.notification.shouldPostAlert
import de.jayp33.sonnenregen.work.isRetryableFailure
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkerLogicTest {
    @Test
    fun one_shot_alert_only_posts_for_its_scheduled_episode() {
        val scheduled = AlertState(AlertStage.SCHEDULED, 1_000L)

        assertTrue(shouldPostAlert(scheduled, 1_000L))
        assertTrue(shouldPostAlert(scheduled, null))
        assertFalse(shouldPostAlert(scheduled, 2_000L))
        assertFalse(shouldPostAlert(null, 1_000L))
        assertFalse(shouldPostAlert(AlertState(AlertStage.NOTIFIED, 1_000L), 1_000L))
        assertFalse(shouldPostAlert(AlertState(AlertStage.ARMED, 1_000L), 1_000L))
    }

    @Test
    fun only_transport_failures_are_retried() {
        assertFalse(isRetryableFailure(ParseException("bad markup")))
        assertFalse(isRetryableFailure(IllegalArgumentException("bad city")))
        assertTrue(isRetryableFailure(SourceException("offline")))
        assertTrue(isRetryableFailure(IllegalStateException("unexpected")))
    }

    @Test
    fun client_rejects_invalid_city_before_any_request() = runTest {
        val result = runCatching { WetterOnlineClient().fetch("../admin") }

        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }
}
