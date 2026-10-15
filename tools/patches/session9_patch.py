#!/usr/bin/env python3
"""Session 9 docs patch (15 Oct 2026): result of the first full Gradle check.

Run from the project root, AFTER the session 7 and 8 patches and AFTER applying payprobe_test_fix.patch:
    git apply --check ~/Downloads/payprobe_test_fix.patch && git apply ~/Downloads/payprobe_test_fix.patch
    python3 ~/Downloads/session9_patch.py
Every replaced text must occur exactly once or nothing is written. Running it twice does nothing.
"""
import sys
from pathlib import Path

H, R, L = Path("docs/PROJECT_HANDOFF.md"), Path("docs/IMPLEMENTATION_ROADMAP.md"), Path("docs/SESSION_LOG.md")
RW = Path("docs/REMAINING_WORK.md")
for p in (H, R, L, RW):
    if not p.is_file():
        sys.exit(f"STOP: {p} not found. Run from ~/Downloads/qos-priority-scheduler (after the session 8 steps)")
h, r, l, rw = H.read_text(), R.read_text(), L.read_text(), RW.read_text()
if "## Session 9:" in l:
    sys.exit("Session 9 entry is already in docs/SESSION_LOG.md. Nothing changed.")
if "## Session 8:" not in l:
    sys.exit("STOP: run the session 8 steps first. Nothing written.")
test = Path("payprobe/src/test/kotlin/dev/netvalve/payprobe/PayProbeCoreTest.kt")
if not test.is_file() or "TinyHttpServer" not in test.read_text():
    sys.exit("STOP: apply payprobe_test_fix.patch first (PayProbeCoreTest.kt has no TinyHttpServer yet). Nothing written.")


def once(t, old, new, where):
    n = t.count(old)
    if n != 1:
        sys.exit(f"STOP: expected 1 match in {where}, found {n}: {old[:70]}... Nothing written.")
    return t.replace(old, new)


# ------------------------------------------------------------------ PROJECT_HANDOFF.md
h = once(h,
    "**code written 14 Oct 2026, not yet built with Gradle, not run on the phone** (M3, Section 9.1)",
    "**code written 14 Oct 2026; the debug build passes on the Mac (15 Oct); not run on the phone** (M3, Section 9.1)",
    "Handoff Section 1")
h = once(h,
    "**code written 14 Oct 2026, not yet built with Gradle, not run on the phone** (M4, M5, Section 9.1)",
    "**code written 14 Oct 2026; the debug build passes on the Mac (15 Oct); not run on the phone** (M4, M5, Section 9.1)",
    "Handoff Section 1")
h = once(h,
    "(The wiring code now exists in the repository but has never been compiled with Gradle or run on the phone; see Section 9.1.)",
    "(The wiring code now exists in the repository and the debug build passes on the Mac (15 Oct 2026), but it has never run on the phone; see Section 9.1.)",
    "Handoff Section 1")
h = once(h,
    "- Android-dependent code (Hilt modules, DataStore, Compose screens, `FlowSupervisor`, `RuleEngine`, `TrafficEngine`) has **not been compiled with Gradle**: it may contain compile errors. Nothing was run on the phone.",
    "- Android-dependent code (Hilt modules, DataStore, Compose screens, `FlowSupervisor`, `RuleEngine`, `TrafficEngine`): `./gradlew :app:assembleDebug -Pnetvalve.netstack=true` and `:payprobe:assembleDebug` **passed on the student's Mac on 15 Oct 2026** (Session 9 check output, run with `-q`). Not verified: the app's unit tests under Gradle, and everything on the phone.",
    "Handoff Section 9.1")
h = once(h,
    "(code written 14 Oct 2026, not yet built with Gradle or run on the phone, Section 9.1)",
    "(code written 14 Oct 2026, debug build passes on the Mac 15 Oct, not run on the phone, Section 9.1)",
    "Handoff Section 11")

lines = h.split("\n")
idx = [i for i, ln in enumerate(lines) if ln.startswith("| 15 | The planned way to put the in-app simulator under the scheduler")]
if len(idx) != 1:
    sys.exit(f"STOP: expected the problem 15 row once in Handoff Section 8, found {len(idx)}. Nothing written.")
lines.insert(idx[0] + 1,
    "| 16 | `./gradlew test` failed in `:payprobe:compileDebugUnitTestKotlin` (found 15 Oct 2026 from the Mac output) | `PayProbeCoreTest.kt` imported the JDK class `com.sun.net.httpserver.HttpServer`, which is not on the Android Gradle unit-test classpath; the 9 PayProbe tests had therefore only ever run in a sandbox, never under Gradle | test server rewritten on a plain `java.net.ServerSocket` (`TinyHttpServer`); 9 of 9 pass in a sandbox in 3 runs with a stand-in runner. Gradle result pending |")
h = "\n".join(lines)

row = "| 14 Oct 2026 | `docs/REMAINING_WORK.md` added (status, remaining work, report update list, prompts) | 15 |"
h = once(h, row, row + "\n| 15 Oct 2026 | Session 9: first full Gradle check; debug builds pass, `./gradlew test` failed in PayProbe tests (problem 16); status wording for M3 to M5 corrected | 1, 8, 9, 11 |",
         "Handoff Section 15")

# ------------------------------------------------------------------ IMPLEMENTATION_ROADMAP.md
r = once(r,
    "moved, was not recorded.",
    "moved, was not recorded. The 9 PayProbe JVM tests had never run under Gradle until 15 Oct 2026 (they failed to compile there, handoff problem 16).",
    "Roadmap M1 status")
r = once(r, "| M3 | Wire scheduler into relay | code written, not built | 14 Oct 2026 |",
         "| M3 | Wire scheduler into relay | code written, debug build passes | 15 Oct 2026 |", "Roadmap tracker")
r = once(r, "Android parts not built with Gradle; nothing run on the phone;",
         "the debug APK builds on the Mac under Gradle (15 Oct); nothing run on the phone;", "Roadmap tracker M3")
r = once(r, "| M4 | Tier UI | code written, not built | 14 Oct 2026 | tier chips in the Apps screen, High limit message, fixed chips; not built, not run |",
         "| M4 | Tier UI | code written, debug build passes | 15 Oct 2026 | tier chips in the Apps screen, High limit message, fixed chips; builds on the Mac, not run on the phone |",
         "Roadmap tracker")
r = once(r, "| M5 | In-app traffic simulator | code written, not built | 14 Oct 2026 |",
         "| M5 | In-app traffic simulator | code written, debug build passes | 15 Oct 2026 |", "Roadmap tracker")

# ------------------------------------------------------------------ REMAINING_WORK.md
rw = once(rw,
    "| M3 wiring into the relay, Dashboard card | code written | pure parts compiled and tested in the sandbox; Android parts never compiled with Gradle; never run on the phone |",
    "| M3 wiring into the relay, Dashboard card | code written, debug build passes | pure parts tested in the sandbox; `:app:assembleDebug` passed on the Mac (15 Oct); app unit tests under Gradle not yet verified; never run on the phone |",
    "REMAINING_WORK table")
rw = once(rw, "| M4 tier chips in the Apps screen | code written | same as M3 |",
          "| M4 tier chips in the Apps screen | code written, debug build passes | same as M3 |", "REMAINING_WORK table")
rw = once(rw,
    "Expect some compile errors in the Android parts. Paste them, fix, repeat until both pass.",
    "Status 15 Oct 2026: both debug APKs build; `./gradlew test` failed in the PayProbe tests (fix: `payprobe_test_fix.patch`, handoff problem 16). Next: run `./gradlew test` again, then `./gradlew :app:testDebugUnitTest` for the 36 + 26 app tests, paste any errors, repeat until both pass.",
    "REMAINING_WORK item 1")

# ------------------------------------------------------------------ SESSION_LOG.md
entry = """
## Session 9: 15 Oct 2026, entry check and the first full Gradle check
**Goal:** run `./tools/session_start_check.sh --full` after the M3 to M5 code, and fix what fails.
**Entry check:** student's Mac output: 17 ok, 3 warnings. HEAD `a973dab` ("Session 8: add REMAINING_WORK.md"), tree clean, `origin/qos-scheduler` equals HEAD (local tracking ref, not a live query), last logged commit `478f4cd` in history, tags present. This shows steps 6 and 8 of Session 8 were done. Warnings: the two 3-row mobile files (known since Session 5, handoff 10.1) and `./gradlew test FAILED` (new). No phone was connected, so section 6 had nothing to check.
**Done (with evidence):**
- `:app:assembleDebug -Pnetvalve.netstack=true` and `:payprobe:assembleDebug` pass on the Mac ("both APKs build"): the M3 to M5 Android code compiles under Gradle, including Hilt, DataStore and Compose. The check runs both with `-q`, so warnings are hidden.
- Failure found: `./gradlew test` stops at `:payprobe:compileDebugUnitTestKotlin`. `PayProbeCoreTest.kt` line 3 imports `com.sun.net.httpserver.HttpServer`, which is unresolved on the Android unit-test classpath. The error is in an M1 test, not in the new code.
- Fix written in an AI sandbox: the test server now uses `java.net.ServerSocket` (`TinyHttpServer` at the end of `PayProbeCoreTest.kt`). 9 of 9 tests pass in the sandbox in 3 runs with a stand-in runner; the old version passed 9 of 9 there too, so the sandbox could not show the Gradle problem. Gradle result: not yet seen.
**Claimed, not verified:**
- That the 9 PayProbe tests pass under Gradle. Earlier notes ("9 JVM tests" for M1) came from a sandbox; they had never run under Gradle.
- Whether the app's unit tests (36 old and 26 new) compile and pass under Gradle: Gradle stopped at the PayProbe failure, so the output says nothing about them.
- Everything on the phone (smoke checks) and all M2 and M6 measurements.
**Decisions:** none new.
**Problems:** handoff problem 16 added; Section 1, 9.1, 11, the roadmap tracker rows M3 to M5 and `REMAINING_WORK.md` now say "debug build passes" instead of "not built".
**Handoff state:**
- HEAD at end: a973dab (the commit that adds this entry follows it)
- Tags: pinned-base, week1-aar-built, scheduler-core, m1-payprobe-done, m2-idle-baselines-mobile
- Tree clean: after `./tools/finish_session.sh`
**Open at end:**
- Run `./gradlew test` again; if it fails, `./gradlew :app:testDebugUnitTest` shows the app tests separately. Paste errors.
- Phone smoke checks (stop the VPN, install, Mode OFF like before; tier chips and persistence; tick NetValve, restart tunnel, simulator shows traffic), then M2 and M6 on the hotspot.
- Cloudflare `__up` check; smaller upstream socket buffers not done; "old project removal" decision; review untouched docs.
- M6 to M8 not started.
**Next session, first steps:**
1. Run `./tools/session_start_check.sh --full` and write the Entry check.
2. Fix whatever still fails in `./gradlew test`, then the phone checks.
"""
if not l.endswith("\n"):
    l += "\n"
l += entry

H.write_text(h); R.write_text(r); L.write_text(l); RW.write_text(rw)
print("Done. Review: git diff --stat")
