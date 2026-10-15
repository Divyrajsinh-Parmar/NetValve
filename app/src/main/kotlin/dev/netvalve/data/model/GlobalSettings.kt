package dev.netvalve.data.model

import dev.netvalve.scheduler.SchedMode
import kotlinx.serialization.Serializable

/**
 * Tunnel-wide settings, persisted via DataStore. These are the knobs that are
 * not specific to any single app.
 *
 * @param selectionMode      ONLY_SELECTED vs ALL_EXCEPT routing.
 * @param defaultDownload    global default download cap applied to a controlled
 *                           app that has no active per-app rule (unlimited ⇒ no
 *                           default shaping).
 * @param defaultUpload      global default upload cap, as above.
 * @param ipv6Mode           RELAY or FAST_REJECT (see [Ipv6Mode]).
 * @param exemptDns          leave DNS (UDP/53, DoT/853) unshaped to protect
 *                           resolution latency for VoIP/streaming/gaming.
 * @param logLevel           minimum log level persisted/shown (see LogLevel).
 * @param autoStartOnBoot    re-arm the tunnel after reboot if consent is held.
 * @param enabled            the user's desired tunnel state (persisted so the
 *                           service can be restored after process death/boot).
 * @param schedMode          priority scheduler mode. Only PRIORITY_PACER is active on the
 *                           device; OFF (default) keeps stock NetValve behaviour.
 * @param schedRateDownBytesPerSec  pacer rate R for download, bytes/s. 0 = not scheduled.
 * @param schedRateUpBytesPerSec    pacer rate R for upload, bytes/s. 0 = not scheduled.
 */
@Serializable
data class GlobalSettings(
    val selectionMode: SelectionMode = SelectionMode.ONLY_SELECTED,
    val defaultDownload: BandwidthLimit = BandwidthLimit.Unlimited,
    val defaultUpload: BandwidthLimit = BandwidthLimit.Unlimited,
    val ipv6Mode: Ipv6Mode = Ipv6Mode.RELAY,
    val exemptDns: Boolean = true,
    val logLevel: Int = 1, // LogLevel.INFO.ordinal
    val autoStartOnBoot: Boolean = false,
    val enabled: Boolean = false,
    val schedMode: SchedMode = SchedMode.OFF,
    val schedRateDownBytesPerSec: Long = 0L,
    val schedRateUpBytesPerSec: Long = 0L,
) {
    companion object {
        // Kept in sync with SelectionMode; ONLY_SELECTED with no default caps is
        // the safe, do-nothing starting point.
        val Default = GlobalSettings()
    }
}
