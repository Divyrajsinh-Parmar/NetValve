package dev.netvalve.ui.setup

/** The permissions the first-run guide asks for, in the order they are asked. */
enum class SetupPermission { NOTIFICATIONS, VPN, BATTERY }

/** What the phone says right now about each permission (read live, never remembered). */
data class PermissionSnapshot(
    val notifications: Boolean,
    val vpn: Boolean,
    val battery: Boolean,
) {
    fun isGranted(permission: SetupPermission): Boolean = when (permission) {
        SetupPermission.NOTIFICATIONS -> notifications
        SetupPermission.VPN -> vpn
        SetupPermission.BATTERY -> battery
    }
}

/**
 * Rules of the first-run guide. Pure Kotlin (no Android, no Compose) so they are tested on a JVM.
 *
 * The guide shows the first permission that is still missing, one at a time. Nothing is stored:
 * every time the app comes to the front the phone is asked again, so a permission that is already
 * granted is never asked a second time, and one that was taken away in Settings is asked again.
 */
object SetupGuide {

    val order: List<SetupPermission> =
        listOf(SetupPermission.NOTIFICATIONS, SetupPermission.VPN, SetupPermission.BATTERY)

    fun missing(snapshot: PermissionSnapshot): List<SetupPermission> =
        order.filter { !snapshot.isGranted(it) }

    fun grantedCount(snapshot: PermissionSnapshot): Int = order.count { snapshot.isGranted(it) }

    /** The VPN permission is the one the app cannot work without, so it can never be skipped. */
    fun canSkip(permission: SetupPermission): Boolean = permission != SetupPermission.VPN

    /**
     * The step to show now, or null when the app may open. Skipped steps (only possible for the
     * ones where [canSkip] is true) do not block.
     */
    fun nextStep(snapshot: PermissionSnapshot, skipped: Set<SetupPermission>): SetupPermission? =
        missing(snapshot).firstOrNull { !canSkip(it) || it !in skipped }
}
