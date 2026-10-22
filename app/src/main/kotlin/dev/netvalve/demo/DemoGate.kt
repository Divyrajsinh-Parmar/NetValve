package dev.netvalve.demo

/**
 * The switch of the live demo (patch E1). In memory only: when the app process ends, the demo is over.
 *
 * While [active]:
 *  - the VPN builder does NOT exclude NetValve's own package, and `RuleEngine.isControlled` treats the
 *    app's own traffic as controlled, so the demo's own downloads and probes pass through the relay
 *    (in the production settings the app is always left out of the tunnel);
 *  - a flow to one of the probe addresses is put on the top priority level (the demo has only one
 *    app UID, so the level must come from the destination).
 *
 * Normal use never sets it. Pure Kotlin, no Android imports.
 */
object DemoGate {
    @Volatile
    var active: Boolean = false
        private set

    @Volatile
    private var probeAddresses: Set<String> = emptySet()

    fun begin(probes: Set<String>) {
        probeAddresses = probes
        active = true
    }

    fun end() {
        active = false
        probeAddresses = emptySet()
    }

    /** True only during the demo and only for the probe's destination address. */
    fun isProbe(address: String?): Boolean = active && address != null && address in probeAddresses
}
