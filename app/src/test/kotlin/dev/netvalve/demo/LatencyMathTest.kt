package dev.netvalve.demo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LatencyMathTest {

    @Test
    fun medianP95MaxJitterAndLoss() {
        // 10 answers: 10..100, one lost
        val s = LatencyMath.summarize(listOf(10L, 20L, 30L, 40L, 50L, 60L, 70L, 80L, 90L, 100L, null))
        assertEquals(11, s.sent)
        assertEquals(1, s.lost)
        assertEquals(10, s.received)
        assertEquals(55L, s.medianMs)
        assertEquals(100L, s.p95Ms)
        assertEquals(100L, s.maxMs)
        assertEquals(10.0, s.jitterMs!!, 0.0001)
        assertEquals(100.0 / 11, s.lossPercent, 0.0001)
    }

    @Test
    fun oddCountMedian() {
        assertEquals(30L, LatencyMath.summarize(listOf(50L, 10L, 30L)).medianMs)
    }

    @Test
    fun emptyAndAllLost() {
        val e = LatencyMath.summarize(emptyList())
        assertNull(e.medianMs); assertNull(e.p95Ms); assertNull(e.jitterMs)
        assertEquals(0.0, e.lossPercent, 0.0)
        val l = LatencyMath.summarize(listOf(null, null))
        assertEquals(100.0, l.lossPercent, 0.0)
        assertNull(l.p95Ms)
    }

    @Test
    fun singleAnswerHasNoJitter() {
        assertNull(LatencyMath.summarize(listOf(40L)).jitterMs)
    }

    private fun score(vararg v: Long?) = LagRules.score(LatencyMath.summarize(v.toList()))

    @Test
    fun scoreLimits() {
        assertEquals(LagScore.NO_DATA, score(10, 10, 10))
        assertEquals(LagScore.GOOD, score(50, 60, 70, 80, 90, 100))
        assertEquals(LagScore.OKAY, score(50, 60, 70, 80, 90, 250))
        assertEquals(LagScore.BAD, score(50, 60, 70, 80, 90, 900))
        assertEquals(LagScore.BAD, score(null, null, null, null, null, null))
        // good timing but 1 of 6 lost (16 percent) is bad
        assertEquals(LagScore.BAD, score(50, 60, 70, 80, 90, null))
    }
}
