package dev.netvalve.scheduler

import org.junit.Assert.assertEquals
import org.junit.Test

class LevelResolverTest {

    private class FakePackages(private val map: Map<Int, List<String>>) {
        var calls = 0
        fun lookup(uid: Int): List<String> { calls++; return map[uid] ?: emptyList() }
    }

    @Test
    fun resolvesAndCachesPerUid() {
        val fake = FakePackages(mapOf(10_001 to listOf("a"), 10_002 to listOf("b")))
        val order = PriorityOrder(listOf("a", "b"))
        val r = LevelResolver(order) { fake.lookup(it) }
        assertEquals(PriorityLevels.FIRST_RANK, r.levelOfUid(10_001))
        assertEquals(PriorityLevels.FIRST_RANK + 1, r.levelOfUid(10_002))
        assertEquals(2, fake.calls)
        repeat(5) { r.levelOfUid(10_001); r.levelOfUid(10_002) }
        assertEquals("cache must absorb repeated lookups", 2, fake.calls)
    }

    @Test
    fun invalidAndUnknownUidsAreNormalWithoutAskingTheSource() {
        val fake = FakePackages(emptyMap())
        val r = LevelResolver(PriorityOrder()) { fake.lookup(it) }
        assertEquals(PriorityLevels.NORMAL, r.levelOfUid(-1))
        assertEquals(PriorityLevels.NORMAL, r.levelOfUid(-2))
        assertEquals(0, fake.calls)
    }

    @Test
    fun reorderTakesEffectAfterInvalidate() {
        val fake = FakePackages(mapOf(10_001 to listOf("a"), 10_002 to listOf("b")))
        val order = PriorityOrder(listOf("a", "b"))
        val r = LevelResolver(order) { fake.lookup(it) }
        assertEquals(PriorityLevels.FIRST_RANK, r.levelOfUid(10_001))
        order.moveDown("a")
        // still the cached answer until the owner invalidates
        assertEquals(PriorityLevels.FIRST_RANK, r.levelOfUid(10_001))
        r.invalidate()
        assertEquals(PriorityLevels.FIRST_RANK + 1, r.levelOfUid(10_001))
        assertEquals(PriorityLevels.FIRST_RANK, r.levelOfUid(10_002))
    }
}
