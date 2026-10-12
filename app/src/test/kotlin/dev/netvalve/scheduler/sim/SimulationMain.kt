package dev.netvalve.scheduler.sim

import java.io.File
import java.util.Locale

/**
 * Runs the three algorithm-level scenarios and (optionally) writes a CSV.
 * The unit test SchedulerSimulationTest prints the same table and checks the success criteria;
 * run this main directly (IDE or kotlinc) only when you want the CSV in docs/data/.
 */
object SimulationMain {

    private val s1 = SimParams()                                  // bursty High app (10 s on, 10 s off)
    private val s4 = SimParams(highOnMs = 0)                      // High never sends: work-conservation
    private val s5 = SimParams(highSaturating = true, highOnMs = 0) // High is a bulk flow: starvation

    private val allModes = SimMode.values().toList()
    // In S5 only the pacer modes are meaningful: without a scheduler the result depends on which
    // flow happens to inject first into the model's FIFO, so those rows would mislead.
    private val s5Modes = listOf(SimMode.MODE_B, SimMode.MODE_B_NO_FLOORS)

    class Scenario(val name: String, val params: SimParams, val modes: List<SimMode>)

    fun scenarios(): List<Scenario> = listOf(
        Scenario("S1 bursty High app + 3 Low downloads", s1, allModes),
        Scenario("S4 High idle, only Low traffic", s4, allModes),
        Scenario("S5 High saturates the link (starvation check)", s5, s5Modes),
    )

    fun toCsv(all: List<Pair<String, List<SimResult>>>): String {
        val sb = StringBuilder()
        sb.append("scenario,config,label,high_samples,p50_ms,p95_ms,p99_ms,max_ms,low_goodput_kBps,low_goodput_high_idle_kBps,low_pct_of_R,high_bulk_kBps\n")
        for ((name, results) in all) for (r in results) {
            val hi = r.highLatenciesMs.size
            sb.append(
                String.format(
                    Locale.US, "%s,%s,\"%s\",%d,%d,%d,%d,%d,%.1f,%.1f,%.1f,%.1f\n",
                    name.substringBefore(' '), r.mode.id, r.mode.label, hi, r.p50, r.p95, r.p99, r.max,
                    r.lowGoodput / 1000, r.lowGoodputWhileHighIdle / 1000,
                    100.0 * r.lowGoodput / r.params.rateR, r.highBulkDeliveredBytes * 1000.0 / r.params.durationMs / 1000,
                ),
            )
        }
        return sb.toString()
    }

    fun table(all: List<Pair<String, List<SimResult>>>): String {
        val sb = StringBuilder()
        for ((name, results) in all) {
            sb.append("\n== $name  (link ${results[0].params.linkBytesPerSec * 8 / 1_000_000} Mbps, R = ${results[0].params.rateR * 8 / 1000} kbps)\n")
            sb.append(String.format(Locale.US, "%-4s %-28s %6s %6s %6s %6s | %9s %9s %7s\n", "id", "config", "p50", "p95", "p99", "max", "Low kB/s", "idleLow", "%R"))
            for (r in results) {
                sb.append(
                    String.format(
                        Locale.US, "%-4s %-28s %6d %6d %6d %6d | %9.1f %9.1f %6.1f%%\n",
                        r.mode.id, r.mode.label, r.p50, r.p95, r.p99, r.max,
                        r.lowGoodput / 1000, r.lowGoodputWhileHighIdle / 1000, 100.0 * r.lowGoodput / r.params.rateR,
                    ),
                )
            }
        }
        return sb.toString()
    }

    fun runAllScenarios(): List<Pair<String, List<SimResult>>> =
        scenarios().map { it.name to Simulation.runAll(it.params, it.modes) }

    @JvmStatic
    fun main(args: Array<String>) {
        val all = runAllScenarios()
        print(table(all))
        if (args.isNotEmpty()) {
            File(args[0]).apply { parentFile?.mkdirs() }.writeText(toCsv(all))
            println("\nCSV written to ${args[0]}")
        }
    }
}
