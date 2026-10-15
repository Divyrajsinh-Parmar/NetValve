package dev.netvalve.scheduler

import org.junit.Assert.assertEquals
import org.junit.Test

class TierResolverTest {

    private class FakePackages(private val map: Map<Int, List<String>>) {
        var calls = 0
        fun lookup(uid: Int): List<String> { calls++; return map[uid] ?: emptyList() }
    }

    @Test
    fun resolvesAndCachesPerUid() {
        val fake = FakePackages(mapOf(10_001 to listOf("a.low"), 10_002 to listOf("b.high")))
        val tiers = TierAssignments(mapOf("a.low" to Tier.LOW, "b.high" to Tier.HIGH))
        val r = TierResolver(tiers) { fake.lookup(it) }
        assertEquals(Tier.LOW, r.tierOfUid(10_001))
        assertEquals(Tier.HIGH, r.tierOfUid(10_002))
        assertEquals(2, fake.calls)
        repeat(5) { r.tierOfUid(10_001); r.tierOfUid(10_002) }
        assertEquals("cache must absorb repeated lookups", 2, fake.calls)
    }

    @Test
    fun invalidAndUnknownUidsAreMediumWithoutAskingTheSource() {
        val fake = FakePackages(emptyMap())
        val r = TierResolver(TierAssignments()) { fake.lookup(it) }
        assertEquals(Tier.MEDIUM, r.tierOfUid(-1))
        assertEquals(Tier.MEDIUM, r.tierOfUid(-2))
        assertEquals(0, fake.calls)
    }

    @Test
    fun unknownPackagesAreMedium() {
        val r = TierResolver(TierAssignments()) { emptyList() }
        assertEquals(Tier.MEDIUM, r.tierOfUid(10_050))
    }

    @Test
    fun sharedUidTakesHighestTier() {
        val tiers = TierAssignments(mapOf("x.low" to Tier.LOW, "x.high" to Tier.HIGH))
        val r = TierResolver(tiers) { listOf("x.low", "x.high", "x.other") }
        assertEquals(Tier.HIGH, r.tierOfUid(10_100))
    }

    @Test
    fun tierChangeIsSeenAfterInvalidate() {
        val tiers = TierAssignments()
        val r = TierResolver(tiers) { listOf("some.app") }
        assertEquals(Tier.MEDIUM, r.tierOfUid(10_200))
        tiers.set("some.app", Tier.HIGH)
        assertEquals("still cached", Tier.MEDIUM, r.tierOfUid(10_200))
        r.invalidate()
        assertEquals(Tier.HIGH, r.tierOfUid(10_200))
    }

    @Test
    fun lockedEntriesWin() {
        val tiers = TierAssignments(locked = mapOf("own.app" to Tier.LOW, "payprobe" to Tier.HIGH))
        val r = TierResolver(tiers) { uid -> if (uid == 1) listOf("own.app") else listOf("payprobe") }
        assertEquals(Tier.LOW, r.tierOfUid(1))
        assertEquals(Tier.HIGH, r.tierOfUid(2))
    }
}
