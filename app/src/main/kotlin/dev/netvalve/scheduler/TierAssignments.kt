package dev.netvalve.scheduler

/**
 * Which package belongs to which tier, with the rules of the roadmap:
 *  - at most [Tier.MAX_HIGH_APPS] apps in High
 *  - locked entries (our own app is always Low, PayProbe is always High) cannot be changed
 *  - everything not assigned is Medium (this also covers unattributed flows)
 *  - a shared-UID group (several packages, one UID) takes the HIGHEST tier among its packages
 *
 * Pure Kotlin so the UI and the relay can share it and tests can cover every rule.
 */
class TierAssignments(
    initial: Map<String, Tier> = emptyMap(),
    private val locked: Map<String, Tier> = emptyMap(),
) {
    enum class SetResult { OK, HIGH_FULL, LOCKED }

    private val assigned = HashMap<String, Tier>().apply {
        initial.forEach { (pkg, tier) -> if (pkg !in locked && tier != Tier.MEDIUM) put(pkg, tier) }
    }

    fun tierOf(packageName: String): Tier = locked[packageName] ?: assigned[packageName] ?: Tier.MEDIUM

    /** Tier of a UID that maps to one or more packages (smallest level = most important). */
    fun tierOfPackages(packages: Collection<String>): Tier =
        if (packages.isEmpty()) Tier.MEDIUM else packages.map { tierOf(it) }.minByOrNull { it.level } ?: Tier.MEDIUM

    fun highPackages(): Set<String> =
        (assigned.filterValues { it == Tier.HIGH }.keys + locked.filterValues { it == Tier.HIGH }.keys)

    fun set(packageName: String, tier: Tier): SetResult {
        if (packageName in locked) return SetResult.LOCKED
        if (tier == Tier.HIGH && assigned[packageName] != Tier.HIGH && highPackages().size >= Tier.MAX_HIGH_APPS) {
            return SetResult.HIGH_FULL
        }
        if (tier == Tier.MEDIUM) assigned.remove(packageName) else assigned[packageName] = tier
        return SetResult.OK
    }

    /** Only the explicit, non-default assignments (what should be persisted). */
    fun snapshot(): Map<String, Tier> = assigned.toMap()
}
