#!/usr/bin/env python3
"""Session 9, patch E (15 Oct 2026): brings the Session 7 entry up to date WITHOUT rewriting it.

The original text of the Session 7 entry stays as written on 14 Oct. Two headings get a short marker and a
"Status update" block is added at the end of the entry, saying for each item whether it is resolved,
superseded or still open.

Run from the project root, AFTER session9d_patch.py:
    python3 ~/Downloads/session9e_patch.py
Docs only. Every replaced text must occur exactly once or nothing is written. Running it twice does nothing.
"""
import sys
from pathlib import Path

L = Path("docs/SESSION_LOG.md")
if not L.is_file():
    sys.exit(f"STOP: {L} not found. Run from ~/Downloads/qos-priority-scheduler")
l = L.read_text()
if "third run, 15 Oct 2026" not in l:
    sys.exit("STOP: run session9d_patch.py first. Nothing written.")
if "**Status update (15 Oct 2026" in l:
    sys.exit("Patch 9E is already applied. Nothing changed.")


def once(t, old, new, where):
    n = t.count(old)
    if n != 1:
        sys.exit(f"STOP: expected 1 match in {where}, found {n}: {old[:70]}... Nothing written.")
    return t.replace(old, new)


l = once(l, "**Open at end:**\n- M2: choose the load network",
         "**Open at end (status update below, 15 Oct 2026):**\n- M2: choose the load network", "Session 7 Open at end")

old_next = ("**Next session, first steps:**\n1. Run `./tools/session_start_check.sh` and write the Entry check.\n"
            "2. Continue M2 from the first item under Open at end.\n")
new_next = ("**Next session, first steps (superseded by the status update below):**\n"
            "1. Run `./tools/session_start_check.sh` and write the Entry check.\n"
            "2. Continue M2 from the first item under Open at end.\n"
            "\n"
            "**Status update (15 Oct 2026, written in Session 9; the text above is left as written on 14 Oct):**\n"
            "- Resolved: \"`FlowSupervisor.kt` does not use the scheduler (M3 not started)\". The scheduler hooks were written in Session 8 and the debug builds of both APKs pass on the Mac (Session 9 check output). The code has not run on the phone.\n"
            "- Resolved: \"no `--full` run since\" and \"Test passes: last Gradle output is from Session 3\". `--full` ran in Session 9. The first `./gradlew test` failed because the 9 PayProbe tests did not compile under Gradle (handoff problem 16), the second run found the simulator stop race (problem 17), and the third run, `./gradlew test --continue`, ended with BUILD SUCCESSFUL. So the earlier \"9 tests\" for M1 were sandbox results only; they pass under Gradle since 15 Oct 2026.\n"
            "- Superseded: the M2 list under Open at end. After the plan change (Session 8) the order is: phone smoke checks first, then M2 and M6 together on the hotspot network (see `docs/REMAINING_WORK.md`).\n"
            "- Still open, unchanged: the network of the three WiFi idle runs is unknown and their setup is not visible in the CSVs; the M1 acceptance items (5-minute run, on-screen p50 and p95 versus the CSV, moving chart); problem 8 (`protect()` fix) rests on the student's report; the Session 6 GitHub screenshot and the remote tags were not checked; the installer and version of Termux; the hotspot details (model, 4G or 5G, bars, data pack); the \"old project removal\" decision; the review of the untouched docs; M6 to M8 not started.\n"
            "- The current list of what is left is the Open at end of the newest session entry and `docs/REMAINING_WORK.md`.\n")
l = once(l, old_next, new_next, "Session 7 Next steps")

L.write_text(l)
print("Done. Review: git diff docs/SESSION_LOG.md")
