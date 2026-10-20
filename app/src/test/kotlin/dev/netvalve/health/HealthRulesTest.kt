package dev.netvalve.health

import dev.netvalve.log.LogCategory
import dev.netvalve.log.LogLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthRulesTest {

    private val now = 10_000_000_000L

    /** Enough priority waiting samples with a small average wait. */
    private fun healthy() = HealthCounters(
        waitMsSum = listOf(0, 100, 0, 0, 0, 0, 0),
        waitCount = listOf(0, 100, 0, 0, 0, 0, 0),
    )

    private fun eval(
        running: Boolean = true,
        paused: Boolean = false,
        apps: Int = 1,
        rate: Long = 1_000_000L,
        c: HealthCounters = healthy(),
    ) = HealthRules.evaluate(running, paused, apps, rate, c, now).level

    @Test
    fun offAndPausedComeFirst() {
        assertEquals(HealthLevel.OFF, eval(running = false))
        assertEquals(HealthLevel.PAUSED, eval(paused = true))
    }

    @Test
    fun noPriorityAppsThenNoSpeed() {
        assertEquals(HealthLevel.NO_PRIORITY_APPS, eval(apps = 0))
        assertEquals(HealthLevel.NO_SPEED, eval(rate = 0L))
    }

    @Test
    fun aRecentFailureMeansCheckButAnOldOneDoesNot() {
        assertEquals(HealthLevel.CHECK, eval(c = healthy().copy(lastFailureAtMs = now - 60_000L)))
        assertEquals(HealthLevel.GOOD, eval(c = healthy().copy(lastFailureAtMs = now - 2 * HealthRules.RECENT_FAILURE_MS)))
    }

    @Test
    fun fewSamplesMeansWaitingForData() {
        val few = HealthCounters(waitMsSum = listOf(0, 10, 0, 0, 0, 0, 0), waitCount = listOf(0, 10, 0, 0, 0, 0, 0))
        assertEquals(HealthLevel.WAITING, eval(c = few))
        assertEquals(HealthLevel.WAITING, eval(c = HealthCounters()))
    }

    @Test
    fun longAverageWaitOfPriorityAppsMeansCheck() {
        val slow = HealthCounters(waitMsSum = listOf(0, 100 * 80L, 0, 0, 0, 0, 0), waitCount = listOf(0, 100, 0, 0, 0, 0, 0))
        assertEquals(HealthLevel.CHECK, eval(c = slow))
    }

    @Test
    fun goodWhenEverythingIsFine() {
        assertEquals(HealthLevel.GOOD, eval())
    }

    @Test
    fun connectFailuresAloneNeverTurnTheStatusToCheck() {
        assertEquals(HealthLevel.GOOD, eval(c = healthy().copy(connectFailures = 500)))
    }

    @Test
    fun classifiesTheLogLinesOfThePhone() {
        val connect = "connect failed TCP:10.111.0.2:55042>192.168.0.1:8013: failed to connect to /192.168.0.1 (port 8013) from /192.0.0.4 (port 49933) after 10000ms"
        assertEquals(FailureKind.CONNECT, HealthRules.classify(LogLevel.WARNING, LogCategory.ERROR, connect))
        assertEquals(FailureKind.ESTABLISH, HealthRules.classify(LogLevel.ERROR, LogCategory.ERROR, "establish failed: boom"))
        assertEquals(
            FailureKind.AUTO_SPEED,
            HealthRules.classify(LogLevel.INFO, LogCategory.SYSTEM, "auto speed: shared download 0 (not scheduled); network MOBILE, measuring failed, no known value: no shaping"),
        )
        assertEquals(FailureKind.AUTO_SPEED, HealthRules.classify(LogLevel.ERROR, LogCategory.ERROR, "auto speed: stopped by an error: x"))
        assertEquals(FailureKind.ENGINE, HealthRules.classify(LogLevel.ERROR, LogCategory.ERROR, "something broke"))
    }

    @Test
    fun normalLinesAndTheExceptionWarningAreNotCountedTwice() {
        assertNull(HealthRules.classify(LogLevel.INFO, LogCategory.SYSTEM, "Tunnel running; controlling 2 app(s)"))
        assertNull(HealthRules.classify(LogLevel.INFO, LogCategory.SYSTEM, "auto speed: shared download 81.5 Mbps; network MOBILE, measured 81.5 Mbps (0.9 x speed)"))
        // the warning of an exception is followed by the INFO line that says "measuring failed,": only that one counts
        assertNull(HealthRules.classify(LogLevel.WARNING, LogCategory.SYSTEM, "auto speed: measuring failed (IOException: timeout)"))
        assertNull(HealthRules.classify(LogLevel.WARNING, LogCategory.SYSTEM, "Paused"))
    }

    @Test
    fun reportContainsTheKeyNumbersAndTheLimits() {
        val c = healthy().copy(runningMs = 3_725_000L, connectFailures = 4, downBytes = listOf(0, 1024, 0, 0, 0, 0, 1024))
        val v = HealthRules.evaluate(true, false, 1, 1_000_000L, c, now)
        val text = HealthReport.text(c, v, listOf("Mobile data" to 10_000_000L, "Wi-Fi" to null), now)
        assertTrue(text, text.contains("1h 2m"))
        assertTrue(text, text.contains("Priority apps' share of all bytes: 50.0 %"))
        assertTrue(text, text.contains("could not be opened (not a scheduler fault): 4"))
        assertTrue(text, text.contains("Mobile data: 80 Mbps"))
        assertTrue(text, text.contains("Wi-Fi: none"))
        assertTrue(text, text.contains("not a measurement of game FPS"))
    }
}
