package dev.netvalve.demo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoGateTest {
    @Test
    fun probeOnlyDuringTheDemoAndOnlyForItsAddress() {
        DemoGate.end()
        assertFalse(DemoGate.active)
        assertFalse(DemoGate.isProbe("1.2.3.4"))
        DemoGate.begin(setOf("1.2.3.4"))
        try {
            assertTrue(DemoGate.active)
            assertTrue(DemoGate.isProbe("1.2.3.4"))
            assertFalse(DemoGate.isProbe("5.6.7.8"))
            assertFalse(DemoGate.isProbe(null))
        } finally {
            DemoGate.end()
        }
        assertFalse(DemoGate.isProbe("1.2.3.4"))
        assertEquals(false, DemoGate.active)
    }
}
