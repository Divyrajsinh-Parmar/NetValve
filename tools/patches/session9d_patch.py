#!/usr/bin/env python3
"""Session 9, patch D (15 Oct 2026): `./gradlew test --continue` ended with BUILD SUCCESSFUL on the Mac.

Run from the project root, AFTER session9c_patch.py:
    python3 ~/Downloads/session9d_patch.py
Docs only. Every replaced text must occur exactly once or nothing is written. Running it twice does nothing.
"""
import sys
from pathlib import Path

H, R, L, RW = (Path("docs/PROJECT_HANDOFF.md"), Path("docs/IMPLEMENTATION_ROADMAP.md"),
               Path("docs/SESSION_LOG.md"), Path("docs/REMAINING_WORK.md"))
for p in (H, R, L, RW):
    if not p.is_file():
        sys.exit(f"STOP: {p} not found. Run from ~/Downloads/qos-priority-scheduler")
h, r, l, rw = H.read_text(), R.read_text(), L.read_text(), RW.read_text()
if "95 of 96" not in l:
    sys.exit("STOP: run session9c_patch.py first. Nothing written.")
if "third run, 15 Oct 2026" in l:
    sys.exit("Patch 9D is already applied. Nothing changed.")


def once(t, old, new, where):
    n = t.count(old)
    if n != 1:
        sys.exit(f"STOP: expected 1 match in {where}, found {n}: {old[:70]}... Nothing written.")
    return t.replace(old, new)


# ---------------- PROJECT_HANDOFF.md
h = once(h, "Gradle result of that fix pending |",
         "Gradle result: `./gradlew test --continue` ended with BUILD SUCCESSFUL on the Mac (15 Oct 2026, third run) |",
         "Handoff problem 16")
h = once(h, "Gradle result pending |",
         "Gradle result: `./gradlew test --continue` ended with BUILD SUCCESSFUL on the Mac (15 Oct 2026, third run) |",
         "Handoff problem 17")
h = once(h,
    "App unit tests under Gradle on 15 Oct 2026: 96 ran, 95 passed, 1 failed (simulator stop race, problem 17; fix pending). Not verified: everything on the phone.",
    "Unit tests under Gradle: the second run on 15 Oct 2026 had 96 app tests, 95 passed and 1 failed (simulator stop race, problem 17); after the fixes for problems 16 and 17 the third run, `./gradlew test --continue`, ended with BUILD SUCCESSFUL (the pasted output showed only the end, so the exact test counts of that run were not seen). Not verified: everything on the phone.",
    "Handoff Section 9.1")
row = "| 15 Oct 2026 | Session 9 (second Mac result): no PayProbe test failure reported; app unit tests 96 ran, 95 passed; simulator stop race fixed (problem 17) | 8, 9 |"
h = once(h, row, row + "\n| 15 Oct 2026 | Session 9 (third Mac result): `./gradlew test --continue` BUILD SUCCESSFUL; problems 16 and 17 closed | 8, 9 |",
         "Handoff Section 15")

# ---------------- IMPLEMENTATION_ROADMAP.md
r = once(r, "(they failed to compile there, handoff problem 16).",
         "(they failed to compile there, handoff problem 16). After the fixes `./gradlew test --continue` ended with BUILD SUCCESSFUL on 15 Oct 2026.",
         "Roadmap M1 status")

# ---------------- REMAINING_WORK.md
rw = once(rw, "app unit tests under Gradle not yet verified;",
          "`./gradlew test --continue` BUILD SUCCESSFUL on the Mac (15 Oct);", "REMAINING_WORK table")
rw = once(rw,
    "Status 15 Oct 2026: both debug APKs build; no PayProbe test failure in the second run (problem 16 fixed); app tests: 96 ran, 95 passed, 1 failed (simulator stop race, problem 17, fix prepared). Next: run `./gradlew test --continue` until it is green.",
    "Status 15 Oct 2026: both debug APKs build and `./gradlew test --continue` ended with BUILD SUCCESSFUL on the Mac, after the fixes for problems 16 and 17. Item 1 is done; the next item is 2, the phone smoke checks. Optional: Gradle warned that its daemon ran out of metaspace (`org.gradle.jvmargs` in `gradle.properties` has `-XX:MaxMetaspaceSize=1g`).",
    "REMAINING_WORK item 1")

# ---------------- SESSION_LOG.md
l = once(l, "Gradle result of this fix: not yet seen.\n",
    "Gradle result of this fix: third run, 15 Oct 2026, `./gradlew test --continue` on the Mac ended with `BUILD SUCCESSFUL in 1m 11s`. The pasted output showed only the end (compiler warnings from old files, a Gradle daemon metaspace warning and the BUILD line), so the exact test counts were not seen.\n",
    "SESSION_LOG Session 9")
l = once(l, "- Whether the simulator fix passes under Gradle. The app's other unit tests: 95 of 96 passed on the Mac (see above).",
    "- The exact number of tests in the final Gradle run (not in the pasted output). The warning that the Gradle daemon ran out of metaspace did not fail the build; its limit is 1g in `gradle.properties` (not changed).",
    "SESSION_LOG Session 9")
l = once(l, "- Run `./gradlew test --continue` until it is green; paste the summary and any failures.",
    "- Build and unit tests are green (15 Oct 2026). Optional: raise the Gradle metaspace limit (not done, not tested).",
    "SESSION_LOG Session 9")
l = once(l, "2. Fix whatever still fails in `./gradlew test`, then the phone checks.",
    "2. Phone smoke checks (see Open at end), then M2 and M6 on the hotspot.",
    "SESSION_LOG Session 9")

H.write_text(h); R.write_text(r); L.write_text(l); RW.write_text(rw)
print("Done. Review: git diff --stat")
