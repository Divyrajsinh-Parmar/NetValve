#!/usr/bin/env python3
"""Session 8 docs patch (14 Oct 2026): plan change + M3 to M5 code written (not yet built on the Mac).

Run from the project root, AFTER session7_patch.py and session7b_patch.py:
    python3 ~/Downloads/session8_patch.py
Every replaced text must occur exactly once or nothing is written. Running it twice does nothing.
Nothing is deleted. Review with `git diff`.
"""
import sys
from pathlib import Path

H, R, L = Path("docs/PROJECT_HANDOFF.md"), Path("docs/IMPLEMENTATION_ROADMAP.md"), Path("docs/SESSION_LOG.md")
for p in (H, R, L):
    if not p.is_file():
        sys.exit(f"STOP: {p} not found. Run from ~/Downloads/qos-priority-scheduler")
h, r, l = H.read_text(), R.read_text(), L.read_text()
if "## Session 8:" in l:
    sys.exit("Session 8 entry is already in docs/SESSION_LOG.md. Nothing changed.")
if "## Session 7:" not in l or "network unknown" not in l:
    sys.exit("STOP: run session7_patch.py and session7b_patch.py first. Nothing written.")


def once(t, old, new, where):
    n = t.count(old)
    if n != 1:
        sys.exit(f"STOP: expected 1 match in {where}, found {n}: {old[:70]}... Nothing written.")
    return t.replace(old, new)


# ------------------------------------------------------------------ PROJECT_HANDOFF.md
h = once(h,
    "| Scheduler wired into the relay (`FlowSupervisor`) and switchable in the app | **not done** (milestone M3) |",
    "| Scheduler wired into the relay (`FlowSupervisor`) and switchable in the app | **code written 14 Oct 2026, not yet built with Gradle, not run on the phone** (M3, Section 9.1) |",
    "Handoff Section 1")
h = once(h,
    "| In-app traffic simulator, tier UI | **not done** (M4, M5) |",
    "| In-app traffic simulator, tier UI | **code written 14 Oct 2026, not yet built with Gradle, not run on the phone** (M4, M5, Section 9.1) |",
    "Handoff Section 1")
h = once(h,
    "the *phone does not use it yet*, so there is no on-device evidence that it helps.",
    "the *phone does not use it yet*, so there is no on-device evidence that it helps. (The wiring code now exists in the repository but has never been compiled with Gradle or run on the phone; see Section 9.1.)",
    "Handoff Section 1")

lines = h.split("\n")
idx = [i for i, ln in enumerate(lines) if ln.startswith("| 14 | The VPN-off (C0) idle run is missing")]
if len(idx) != 1:
    sys.exit(f"STOP: expected the problem 14 row once in Handoff Section 8, found {len(idx)}. Nothing written.")
lines.insert(idx[0] + 1,
    "| 15 | The planned way to put the in-app simulator under the scheduler (tick NetValve in the Apps list) could not work | `RuleEngine.isControlled` returned false for our own UID (\"never shape ourselves\"), so our own flows were relayed unshaped and skipped the scheduler (found 14 Oct 2026 by reading the code) | changed: our own UID counts as controlled only when NetValve is ticked in ONLY_SELECTED mode (ALL_EXCEPT still excludes it). Code written, not built, not tested on the phone |")
h = "\n".join(lines)

section = """### 9.1 M3 to M5 code (written 14 Oct 2026, plan change: build first, test afterwards)

**Plan change (student's decision, 14 Oct 2026):** only a few days are left, so the infrastructure (M3 wiring, M4 tier UI, M5 simulator) is built first; testing, M2 measurements and fixes follow. M2 and M6 are run together later on the hotspot network (the network of the earlier WiFi idle runs is unknown). Risk accepted: if the phone effect turns out small, the M3 to M5 effort was spent before knowing.

**Evidence level (read this before trusting the code).**
- Pure Kotlin (`scheduler/` and `sim/`) compiled with kotlinc 2.0.21 with warnings as errors, and **62 JVM tests passed** (the 36 old ones plus 26 new) in an AI sandbox using a stand-in test runner, not Gradle. The traffic simulator was exercised only against a local server on a JVM.
- Android-dependent code (Hilt modules, DataStore, Compose screens, `FlowSupervisor`, `RuleEngine`, `TrafficEngine`) has **not been compiled with Gradle**: it may contain compile errors. Nothing was run on the phone.

**New pure Kotlin (package `scheduler`, no Android imports).** `SchedulerSettings` and `RateUnits` (Mbps to bytes per second, parsing and formatting), `TierResolver` (uid to tier, cached, generation counter so a stale lookup cannot be stored after a tier change), `SchedulerController` (the one object the relay calls: `newFlowId`, `isActive`, `acquire`, `releaseFlow`, `describeFlow`; follows the settings flow; mode OFF or rate 0 returns at once). `TierAssignments` methods are now `@Synchronized` (relay threads read while the UI writes). Differs from the roadmap text: one `SchedulerController` instead of passing a pacer and a resolver separately to `FlowSupervisor`.

**New Android code.** `TierRepository` and `DataStoreTierRepository` (DataStore key `tiers_json`, no Room), `TierStore` (own package Low through `context.packageName`, `dev.netvalve.payprobe` High, saved tiers load asynchronously, until then everything is Medium), providers in `EngineModule` and `RepositoryModule`, `GlobalSettings` gained `schedMode`, `schedRateDownBytesPerSec`, `schedRateUpBytesPerSec` (defaults keep old stored JSON readable), `TrafficEngine` passes the controller to `FlowSupervisor` (new LAST constructor parameter, default null, so the instrumented test still compiles).

**Relay (`FlowSupervisor`).** At the four sites `sched?.acquire(uid, flowId, lane, bytes)` runs before `throttleManager.pace(...)`, so static caps still apply on top. Flows that are paused, not controlled, or DNS-exempt do not take part. TCP checks the mode on every chunk. UDP upload is routed through the pacing queue only if the scheduler is on for upload when the flow opens (a mode change reaches UDP flows opened afterwards). `releaseFlow` runs in both `finally` blocks. A debug log line per flow shows tier, mode and R. Decision (roadmap item 7): UDP keeps `DROP_OLDEST`. Not done: smaller upstream socket buffers (roadmap risk note).

**UI.** Dashboard card "Priority scheduler" (switch, R download and R upload in Mbps, Save rates). Apps screen: tier chip on controlled apps (tap: Medium, High, Low, Medium), High holds at most 3 apps and a fourth goes to Low with an explanation, fixed chips for our app (Low) and PayProbe (High), "Not controlled" label otherwise. New bottom tab "Load": the traffic simulator (3 parallel downloads, 2 parallel uploads, each a separate HTTP/1.1 connection over a raw socket, live rates and totals, auto-stop default 60 s). Default endpoints are `speed.cloudflare.com` `__down` and `__up`; whether `__up` accepts the 25 MB POST bodies is **not verified**. The simulator runs under our own UID, so NetValve itself must be ticked in the Apps list in ONLY_SELECTED mode.

**New files.** `scheduler/SchedulerSettings.kt`, `TierResolver.kt`, `SchedulerController.kt`, `repository/TierRepository.kt`, `service/TierStore.kt`, `sim/SimHttp.kt`, `sim/TrafficSimulator.kt`, `ui/sim/SimulatorScreen.kt`, `ui/sim/SimulatorViewModel.kt`; tests `RateUnitsTest`, `TierResolverTest`, `SchedulerControllerTest`, `TrafficSimulatorTest`. Changed: `GlobalSettings`, `RuleEngine`, `FlowSupervisor`, `TrafficEngine`, `EngineModule`, `RepositoryModule`, `TierAssignments`, `AppSelectionViewModel`, `AppsScreen`, `DashboardViewModel`, `DashboardScreen`, `Navigation`.

**Not verified yet:** Gradle build and KSP/Hilt wiring; DataStore persistence of tiers; that Mode OFF behaves exactly like before on the phone; chip behaviour and the High limit in the UI; the simulator on a real network and its traffic appearing as our own app and being paced as Low; whether Mode B lowers PayProbe p95 (that is the M2/M6 question).

## 10. Results so far (simulation, plus first phone baselines)"""
h = once(h, "## 10. Results so far (simulation, plus first phone baselines)", section, "Handoff Section 9/10")

h = once(h,
    "M3 wire the scheduler into the relay · M4 tier UI · M5 in-app traffic simulator",
    "M3 wire the scheduler into the relay, M4 tier UI and M5 in-app traffic simulator (code written 14 Oct 2026, not yet built with Gradle or run on the phone, Section 9.1)",
    "Handoff Section 11")

last = "| 14 Oct 2026 | Network of the WiFi idle runs is unknown (not recoverable); they are repeated on the load network | 10 |"
h = once(h, last, last + "\n| 14 Oct 2026 | Plan change: M3, M4, M5 code written before M2 and M6 testing; Section 9.1 added; problem 15 (own UID never controlled) added | 1, 8, 9, 11 |",
         "Handoff Section 15")

# ------------------------------------------------------------------ IMPLEMENTATION_ROADMAP.md
r = once(r,
    "1. **Measure first, build second.** PayProbe",
    "1. **Measure first, build second.** *(Superseded on 14 Oct 2026: with only a few days left, M3 to M5 are built first and tested afterwards; M2 and M6 are run together later. The risk is accepted.)* PayProbe",
    "Roadmap Section 1")
r = once(r,
    "- **After M2:** GO, GO with fixed R, or fallback.",
    "- **After M2 (now run together with M6 because of the 14 Oct 2026 plan change):** GO, GO with fixed R, or fallback. If the phone effect is small, M3 to M5 were built before knowing it.",
    "Roadmap Section 8")
r = once(r, "| M3 | Wire scheduler into relay | not started | | |",
    "| M3 | Wire scheduler into relay | code written, not built | 14 Oct 2026 | `SchedulerController`, tier store, hooks at the 4 call sites, Dashboard card; pure parts compiled and 62 JVM tests pass in a sandbox; Android parts not built with Gradle; nothing run on the phone; `RuleEngine` own-UID change (handoff problem 15) |",
    "Roadmap tracker")
r = once(r, "| M4 | Tier UI | not started | | |",
    "| M4 | Tier UI | code written, not built | 14 Oct 2026 | tier chips in the Apps screen, High limit message, fixed chips; not built, not run |",
    "Roadmap tracker")
r = once(r, "| M5 | In-app traffic simulator | not started | | |",
    "| M5 | In-app traffic simulator | code written, not built | 14 Oct 2026 | Load tab, raw HTTP/1.1 sockets, 3 down + 2 up, auto-stop; tested against a local server on a JVM only; Cloudflare `__up` not verified |",
    "Roadmap tracker")

# ------------------------------------------------------------------ SESSION_LOG.md
entry = """
## Session 8: 14 Oct 2026, plan change and M3 to M5 code written
**Goal:** the student asked to change the plan: build all infrastructure first (M3 wiring, M4 tier UI, M5 simulator), test and fix afterwards, because only a few days are left.
**Decisions:** (a) plan change accepted with the risk written down (roadmap Section 1 and 8, handoff Section 9.1). (b) Own UID counts as controlled only when NetValve is ticked in ONLY_SELECTED mode (handoff problem 15). (c) One `SchedulerController` object is given to `FlowSupervisor`. (d) UDP keeps `DROP_OLDEST`; UDP picks the scheduler on or off when a flow opens. (e) Simulator uses raw HTTP/1.1 sockets so every connection is separate.
**Done (with evidence), checked by the AI in a sandbox on a copy of the project; the student's Mac and phone were not observed:**
- Pure Kotlin (`scheduler/`, `sim/`) compiled with kotlinc 2.0.21 and warnings as errors.
- 62 JVM tests pass in the sandbox with a stand-in test runner: the 36 old scheduler tests plus 26 new ones (`RateUnitsTest` 6, `TierResolverTest` 6, `SchedulerControllerTest` 6, `TrafficSimulatorTest` 8). Not run with Gradle.
- Code written for M3, M4 and M5 as listed in handoff Section 9.1 (new and changed files).
**Claimed, not verified:**
- Everything Android-dependent (Hilt and KSP wiring, DataStore, Compose screens, `FlowSupervisor`, `RuleEngine`, `TrafficEngine`): never compiled with Gradle.
- Nothing ran on the phone: Mode OFF unchanged behaviour, tier persistence, chips, High limit, simulator through the tunnel, Mode B effect on PayProbe.
- Cloudflare `__up` accepting 25 MB POST bodies; the HTTP/1.1 raw-socket assumption on a real TLS server.
**Problems:** the roadmap's M5 instruction (tick NetValve in the Apps list) would not have worked because of the own-UID guard in `RuleEngine`; fixed in code, see handoff problem 15.
**Handoff state:**
- HEAD at end: 478f4cd (the code commit and the commit that adds this entry follow it; later hashes are in `git log`)
- Tags: pinned-base, week1-aar-built, scheduler-core, m1-payprobe-done, m2-idle-baselines-mobile
- Tree clean: after `./tools/finish_session.sh`
**Open at end:**
- Build: `./gradlew test` and `./gradlew :app:assembleDebug -Pnetvalve.netstack=true` on the Mac (or `./tools/session_start_check.sh --full`); fix compile errors by pasting them to the AI.
- Phone checks in this order: (1) stop the VPN, install, Mode OFF behaves like before; (2) tier chips and persistence, fourth High app refused; (3) tick NetValve in Apps, restart tunnel, simulator Down shows traffic; (4) then M2 and M6 on the hotspot (link speed, R, idle baselines C1, C0, C1 again, load runs).
- Cloudflare `__up` check; smaller upstream socket buffers (roadmap risk note) not done.
- Decide what "old project removal" means for the GitHub fork (nothing was deleted); review the untouched docs for stale statements.
- M6 to M8 not started.
**Next session, first steps:**
1. Run `./tools/session_start_check.sh --full` and write the Entry check (build and test results decide what is next).
2. Fix whatever does not compile, then the phone checks above.
"""
if not l.endswith("\n"):
    l += "\n"
l += entry

H.write_text(h); R.write_text(r); L.write_text(l)
print("Done. Review: git diff --stat")
