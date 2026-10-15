#!/usr/bin/env python3
"""Session 9, patch B (15 Oct 2026): result of the first Gradle test run on the Mac and the Prober timeout fix.

Run from the project root, AFTER session9_patch.py and AFTER applying prober_timeout_fix.patch:
    git apply --check ~/Downloads/prober_timeout_fix.patch && git apply ~/Downloads/prober_timeout_fix.patch
    python3 ~/Downloads/session9b_patch.py
Every replaced text must occur exactly once or nothing is written. Running it twice does nothing.
"""
import sys
from pathlib import Path

H, L = Path("docs/PROJECT_HANDOFF.md"), Path("docs/SESSION_LOG.md")
for p in (H, L):
    if not p.is_file():
        sys.exit(f"STOP: {p} not found. Run from ~/Downloads/qos-priority-scheduler")
h, l = H.read_text(), L.read_text()
if "## Session 9:" not in l:
    sys.exit("STOP: run session9_patch.py first. Nothing written.")
if "8 of 9 passed on the Mac" in l:
    sys.exit("Patch 9B is already applied. Nothing changed.")
prober = Path("payprobe/src/main/kotlin/dev/netvalve/payprobe/Prober.kt")
if not prober.is_file() or "noteFor" not in prober.read_text():
    sys.exit("STOP: apply prober_timeout_fix.patch first (Prober.kt has no noteFor yet). Nothing written.")


def once(t, old, new, where):
    n = t.count(old)
    if n != 1:
        sys.exit(f"STOP: expected 1 match in {where}, found {n}: {old[:70]}... Nothing written.")
    return t.replace(old, new)


# ---------------- PROJECT_HANDOFF.md
h = once(h, "Gradle result pending |",
    "Gradle result 15 Oct 2026 (Mac): the tests compile and 8 of 9 pass; `hungServerIsCutOffAtTheTimeoutAndStoredAsTheCap` failed on its note check. `Prober` now stores every kind of timeout (hard cap, connect, read) as \"timeout\" at the cap, and a new test covers it; Gradle result of that fix pending |",
    "Handoff problem 16")
row = "| 15 Oct 2026 | Session 9: first full Gradle check; debug builds pass, `./gradlew test` failed in PayProbe tests (problem 16); status wording for M3 to M5 corrected | 1, 8, 9, 11 |"
h = once(h, row, row + "\n| 15 Oct 2026 | Session 9 (Mac result): PayProbe tests compile, 8 of 9 pass; `Prober` timeout notes made consistent (problem 16) | 8 |", "Handoff Section 15")

# ---------------- SESSION_LOG.md
l = once(l, "Gradle result: not yet seen.\n",
    "Gradle result: see the next bullets.\n"
    "- Mac output after that fix: `:payprobe` tests now compile under Gradle. 9 tests ran, 8 passed, 1 failed in task `:payprobe:testReleaseUnitTest`: `hungServerIsCutOffAtTheTimeoutAndStoredAsTheCap`, `ComparisonFailure` at `PayProbeCoreTest.kt:106`, the line that compares the stored note with \"timeout\". Gradle stopped there, so the app's unit tests did not run. The compiler warnings in the output (`DnsCache.kt`, `DeviceStateMonitorImpl.kt`, `LogsScreen.kt`, `Navigation.kt:52`) are not from the M3 to M5 code.\n"
    "- Hypothesis (not confirmed, the actual note was not in the output): the socket read timeout fired just before the 400 ms hard cap, so the note was the exception name instead of \"timeout\". In the sandbox the same test returned \"timeout\" 40 of 40 times, so the race could not be reproduced there.\n"
    "- Fix written in the sandbox: `Prober` stores `SocketTimeoutException` as note \"timeout\" at the cap (new `noteFor`), a new test `everyKindOfTimeoutIsNotedAsTimeout`, and the assertions of the failing test now print the actual note, latency and wall time. 10 of 10 pass in the sandbox in 3 runs with a stand-in runner. Gradle result of this fix: not yet seen.\n",
    "SESSION_LOG Session 9")
l = once(l, "- That the 9 PayProbe tests pass under Gradle. Earlier notes",
    "- That all PayProbe tests pass under Gradle: 8 of 9 passed on the Mac on 15 Oct (see above), the fix for the 9th is untested there. Earlier notes",
    "SESSION_LOG Session 9")
l = once(l, "- Run `./gradlew test` again; if it fails, `./gradlew :app:testDebugUnitTest` shows the app tests separately. Paste errors.",
    "- Run `./gradlew test --continue` (so the app's unit tests also run after a PayProbe failure). Paste the summary and any failures.",
    "SESSION_LOG Session 9")

H.write_text(h); L.write_text(l)
print("Done. Review: git diff --stat")
