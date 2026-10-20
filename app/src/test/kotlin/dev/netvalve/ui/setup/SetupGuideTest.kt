package dev.netvalve.ui.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupGuideTest {

    private val none = PermissionSnapshot(notifications = false, vpn = false, battery = false)
    private val all = PermissionSnapshot(notifications = true, vpn = true, battery = true)

    @Test
    fun askedOneByOneInTheFixedOrder() {
        assertEquals(SetupPermission.NOTIFICATIONS, SetupGuide.nextStep(none, emptySet()))
        val afterNotifications = none.copy(notifications = true)
        assertEquals(SetupPermission.VPN, SetupGuide.nextStep(afterNotifications, emptySet()))
        val afterVpn = afterNotifications.copy(vpn = true)
        assertEquals(SetupPermission.BATTERY, SetupGuide.nextStep(afterVpn, emptySet()))
        assertNull(SetupGuide.nextStep(all, emptySet()))
    }

    @Test
    fun aGrantedPermissionIsNeverAskedAgain() {
        // Only the VPN permission is missing: the guide goes straight to it.
        val s = all.copy(vpn = false)
        assertEquals(listOf(SetupPermission.VPN), SetupGuide.missing(s))
        assertEquals(SetupPermission.VPN, SetupGuide.nextStep(s, emptySet()))
    }

    @Test
    fun appOpensWhenEverythingIsGranted() {
        assertNull(SetupGuide.nextStep(all, emptySet()))
        assertEquals(3, SetupGuide.grantedCount(all))
        assertEquals(0, SetupGuide.grantedCount(none))
    }

    @Test
    fun aPermissionTakenAwayLaterIsAskedAgain() {
        assertNull(SetupGuide.nextStep(all, emptySet()))
        assertEquals(SetupPermission.BATTERY, SetupGuide.nextStep(all.copy(battery = false), emptySet()))
    }

    @Test
    fun notificationsAndBatteryCanBeSkippedButVpnCannot() {
        assertTrue(SetupGuide.canSkip(SetupPermission.NOTIFICATIONS))
        assertTrue(SetupGuide.canSkip(SetupPermission.BATTERY))
        assertFalse(SetupGuide.canSkip(SetupPermission.VPN))

        val skipped = setOf(SetupPermission.NOTIFICATIONS, SetupPermission.BATTERY)
        // Both skippable steps skipped, VPN granted: the app may open.
        assertNull(SetupGuide.nextStep(none.copy(vpn = true), skipped))
        // A skip entry for VPN is ignored.
        assertEquals(
            SetupPermission.VPN,
            SetupGuide.nextStep(none, skipped + SetupPermission.VPN),
        )
    }

    @Test
    fun skippedStepIsPassedOverToTheNextMissingOne() {
        assertEquals(
            SetupPermission.VPN,
            SetupGuide.nextStep(none, setOf(SetupPermission.NOTIFICATIONS)),
        )
    }
}
