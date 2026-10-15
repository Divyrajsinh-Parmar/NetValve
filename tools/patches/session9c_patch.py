#!/usr/bin/env python3
"""Session 9, patch C (15 Oct 2026): second Mac result and the simulator stop race fix.

Run from the project root, AFTER session9b_patch.py and AFTER applying simulator_stop_fix.patch:
    git apply --check ~/Downloads/simulator_stop_fix.patch && git apply ~/Downloads/simulator_stop_fix.patch
    python3 ~/Downloads/session9c_patch.py
Every replaced text must occur exactly once or nothing is written. Running it twice does nothing.
"""
import sys
from pathlib import Path

H, L, RW = Path("docs/PROJECT_HANDOFF.md"), Path("docs/SESSION_LOG.md"), Path("docs/REMAINING_WORK.md")
for p in (H, L, RW):
    if not p.is_file():
        sys.exit(f"STOP: {p} not found. Run from ~/Downloads/qos-priority-scheduler")
h, l, rw = H.read_text(), L.read_text(), RW.read_text()
if "8 of 9 passed on the Mac" not in l:
    sys.exit("STOP: run session9b_patch.py first. Nothing written.")
if "95 of 96" in l:
    sys.exit("Patch 9C is already applied. Nothing changed.")
sim = Path("app/src/main/kotlin/dev/netvalve/sim/TrafficSimulator.kt")
if not sim.is_file() or "for (s in sockets)" not in sim.read_text():
    sys.exit("STOP: apply simulator_stop_fix.patch first. Nothing written.")


def once(t, old, new, where):
    n = t.count(old)
    if n != 1:
        sys.exit(f"STOP: expected 1 match in {where}, found {n}: {old[:70]}... Nothing written.")
    return t.replace(old, new)


# ---------------- PROJECT_HANDOFF.md
h = once(h,
    "Not verified: the app's unit tests under Gradle, and everything on the phone.",
    "App unit tests under Gradle on 15 Oct 2026: 96 ran, 95 passed, 1 failed (simulator stop race, problem 17; fix pending). Not verified: everything on the phone.",
    "Handoff Section 9.1")

lines = h.split("\n")
idx = [i for i, ln in enumerate(lines) if ln.startswith("| 16 | `./gradlew test` failed in `:payprobe:compileDebugUnitTestKotlin`")]
if len(idx) != 1:
    sys.exit(f"STOP: expected the problem 16 row once in Handoff Section 8, found {len(idx)}. Nothing written.")
lines.insert(idx[0] + 1,
    "| 17 | `TrafficSimulatorTest.downloadUsesThreeParallelConnectionsAndCountsBytes` failed under Gradle with `NoSuchElementException` at the `sim.stop()` line (15 Oct 2026, Mac output) | `TrafficSimulator.stopInternal` called `sockets.toList()` on a concurrent set while worker threads removed their sockets; Kotlin's `toList()` can throw `NoSuchElementException` in that moment (a small sandbox demo gave 38,489 failures in 2 million tries, none with a plain loop). It is a real bug that could hit the Stop button. The stack trace itself was not seen, so the link to this exact line is likely, not proven | changed to a plain `for` loop and added a 100-round start/stop stress test; 63 of 63 pure tests pass in the sandbox in 3 runs (the race itself could not be reproduced in the simulator there). Gradle result pending |")
h = "\n".join(lines)

row = "| 15 Oct 2026 | Session 9 (Mac result): PayProbe tests compile, 8 of 9 pass; `Prober` timeout notes made consistent (problem 16) | 8 |"
h = once(h, row, row + "\n| 15 Oct 2026 | Session 9 (second Mac result): no PayProbe test failure reported; app unit tests 96 ran, 95 passed; simulator stop race fixed (problem 17) | 8, 9 |",
         "Handoff Section 15")

# ---------------- SESSION_LOG.md
l = once(l, "Gradle result of this fix: not yet seen.\n",
    "Gradle result of this fix: see the next bullets.\n"
    "- Second Mac run (`./gradlew test --continue`): the output shows no PayProbe test failure; the only failing task is `:app:testDebugUnitTest`: 96 tests ran, 95 passed, 1 failed: `TrafficSimulatorTest.downloadUsesThreeParallelConnectionsAndCountsBytes` with `NoSuchElementException` at `TrafficSimulatorTest.kt:140`, the `sim.stop()` line.\n"
    "- Cause (likely, the stack trace was not in the output): `TrafficSimulator.stopInternal` used `sockets.toList()` on a concurrent set while workers removed their sockets. A sandbox demo with the same Kotlin call gave 38,489 `NoSuchElementException` in 2 million tries, none with a plain `for` loop. The race could not be reproduced inside the simulator in the sandbox (400 start/stop rounds, 3 times, no failure).\n"
    "- Fix written in the sandbox: plain `for` loop in `stopInternal` and a new stress test `repeatedStartAndStopNeverThrows` (100 start/stop rounds). 63 of 63 pure tests pass in the sandbox in 3 runs (old 36, new 27) with the stand-in runner. Gradle result of this fix: not yet seen.\n",
    "SESSION_LOG Session 9")
l = once(l,
    "- Whether the app's unit tests (36 old and 26 new) compile and pass under Gradle: Gradle stopped at the PayProbe failure, so the output says nothing about them.",
    "- Whether the simulator fix passes under Gradle. The app's other unit tests: 95 of 96 passed on the Mac (see above).",
    "SESSION_LOG Session 9")
l = once(l,
    "- Run `./gradlew test --continue` (so the app's unit tests also run after a PayProbe failure). Paste the summary and any failures.",
    "- Run `./gradlew test --continue` until it is green; paste the summary and any failures.",
    "SESSION_LOG Session 9")

# ---------------- REMAINING_WORK.md
rw = once(rw,
    "Status 15 Oct 2026: both debug APKs build; `./gradlew test` failed in the PayProbe tests (fix: `payprobe_test_fix.patch`, handoff problem 16). Next: run `./gradlew test` again, then `./gradlew :app:testDebugUnitTest` for the 36 + 26 app tests, paste any errors, repeat until both pass.",
    "Status 15 Oct 2026: both debug APKs build; no PayProbe test failure in the second run (problem 16 fixed); app tests: 96 ran, 95 passed, 1 failed (simulator stop race, problem 17, fix prepared). Next: run `./gradlew test --continue` until it is green.",
    "REMAINING_WORK item 1")

H.write_text(h); L.write_text(l); RW.write_text(rw)
print("Done. Review: git diff --stat")
