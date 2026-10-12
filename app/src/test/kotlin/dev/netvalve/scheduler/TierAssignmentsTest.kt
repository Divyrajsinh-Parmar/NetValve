package dev.netvalve.scheduler

import dev.netvalve.scheduler.TierAssignments.SetResult
import org.junit.Assert.assertEquals
import org.junit.Test

class TierAssignmentsTest {

    private fun newAssignments() = TierAssignments(
        locked = mapOf("dev.netvalve.debug" to Tier.LOW, "dev.netvalve.payprobe" to Tier.HIGH),
    )

    @Test
    fun defaultsAreMediumAndLockedEntriesWin() {
        val t = newAssignments()
        assertEquals(Tier.MEDIUM, t.tierOf("com.example.unknown"))
        assertEquals(Tier.LOW, t.tierOf("dev.netvalve.debug"))
        assertEquals(Tier.HIGH, t.tierOf("dev.netvalve.payprobe"))
    }

    @Test
    fun lockedEntriesCannotBeChanged() {
        val t = newAssignments()
        assertEquals(SetResult.LOCKED, t.set("dev.netvalve.debug", Tier.HIGH))
        assertEquals(Tier.LOW, t.tierOf("dev.netvalve.debug"))
    }

    @Test
    fun highTierHoldsAtMostThreeAppsIncludingLockedOnes() {
        val t = newAssignments() // PayProbe already occupies one High slot
        assertEquals(SetResult.OK, t.set("a", Tier.HIGH))
        assertEquals(SetResult.OK, t.set("b", Tier.HIGH))
        assertEquals(SetResult.HIGH_FULL, t.set("c", Tier.HIGH))
        assertEquals(Tier.MEDIUM, t.tierOf("c"))
        // Re-assigning an app that is already High is fine, and freeing a slot works.
        assertEquals(SetResult.OK, t.set("a", Tier.HIGH))
        assertEquals(SetResult.OK, t.set("a", Tier.LOW))
        assertEquals(SetResult.OK, t.set("c", Tier.HIGH))
    }

    @Test
    fun sharedUidTakesTheHighestTier() {
        val t = newAssignments()
        t.set("x", Tier.LOW); t.set("y", Tier.HIGH)
        assertEquals(Tier.HIGH, t.tierOfPackages(listOf("x", "y")))
        assertEquals(Tier.MEDIUM, t.tierOfPackages(emptyList()))
    }

    @Test
    fun snapshotKeepsOnlyExplicitAssignments() {
        val t = newAssignments()
        t.set("a", Tier.HIGH); t.set("b", Tier.LOW); t.set("c", Tier.LOW); t.set("c", Tier.MEDIUM)
        assertEquals(mapOf("a" to Tier.HIGH, "b" to Tier.LOW), t.snapshot())
        val restored = TierAssignments(t.snapshot(), mapOf("dev.netvalve.debug" to Tier.LOW))
        assertEquals(Tier.HIGH, restored.tierOf("a"))
    }
}
