#!/usr/bin/env python3
"""Session 7, patch B (14 Oct 2026). Run AFTER session7_patch.py, from the project root:
    python3 ~/Downloads/session7b_patch.py
Records: the WiFi network of the idle runs is unknown (student cannot remember), so those runs are
repeated on the load network; Termux is installed; WiFi was off when checked.
Every replaced text must occur exactly once or nothing is written. Running it twice does nothing.
"""
import sys
from pathlib import Path

H, R, L = Path("docs/PROJECT_HANDOFF.md"), Path("docs/IMPLEMENTATION_ROADMAP.md"), Path("docs/SESSION_LOG.md")
for p in (H, R, L):
    if not p.is_file():
        sys.exit(f"STOP: {p} not found. Run from ~/Downloads/qos-priority-scheduler")
h, r, l = H.read_text(), R.read_text(), L.read_text()
if "## Session 7:" not in l:
    sys.exit("STOP: run session7_patch.py first (no Session 7 entry yet). Nothing written.")
if "network unknown" in l:
    sys.exit("Patch B is already applied. Nothing changed.")


def once(t, old, new, where):
    n = t.count(old)
    if n != 1:
        sys.exit(f"STOP: expected 1 match in {where}, found {n}: {old[:70]}... Nothing written.")
    return t.replace(old, new)


h = once(h, "WiFi name, band and signal were not recorded in the repo.",
         "WiFi name, band and signal were not recorded in the repo, and the student cannot remember which network it was "
         "(asked 14 Oct 2026), so these three runs count as **network unknown** and are repeated on the load network.",
         "PROJECT_HANDOFF.md 10.2")
row = "| 14 Oct 2026 | Session 7 entry check: tables 10.1 and 10.2 recomputed from the CSVs (they match); problem 13 first-10-samples check; `--skip 10` rule; Section 6 commit rows corrected | 6, 8, 10 |"
h = once(h, row, row + "\n| 14 Oct 2026 | Network of the WiFi idle runs is unknown (not recoverable); they are repeated on the load network | 10 |",
         "PROJECT_HANDOFF.md 15")
r = once(r, "and two identical VPN-on runs differ by 66 ms at p95.",
         "and two identical VPN-on runs differ by 66 ms at p95. The network of those WiFi runs is unknown (not recoverable), "
         "so they are repeated on the load network before the load runs.",
         "IMPLEMENTATION_ROADMAP.md M2")
l = once(l, "- Which WiFi the runs used (own router, hotspot or college) and its name, band and signal: not recorded.",
         "- Which WiFi the runs used (own router, hotspot or college) and its name, band and signal: not recorded; the student cannot remember "
         "(asked 14 Oct 2026). Treated as network unknown.", "SESSION_LOG.md")
l = once(l, "**Claimed, not verified:**\n- Setup of the three WiFi runs",
         "- Phone check (student's terminal output): `adb shell cmd wifi status` printed \"Wifi is disabled\" (so no SSID, band or signal yet); "
         "`pm list packages` shows `com.termux`, so Termux is available as the load source. Its installer and version are not checked yet.\n"
         "**Claimed, not verified:**\n- Setup of the three WiFi runs", "SESSION_LOG.md")
l = once(l, "**Decisions:** none new.",
         "**Decisions:** the WiFi idle runs are kept as evidence but labelled network unknown; idle baselines (C1, C0, C1) are repeated on the "
         "network chosen for the load runs. Load source: Termux with parallel `curl` jobs.", "SESSION_LOG.md")
l = once(l, "- M2: record WiFi name, band and signal; measure link speed on that WiFi and set R = 0.9 x downlink;",
         "- M2: choose the load network (own router or hotspot), turn WiFi on and record name, band and signal; repeat the idle baselines C1, C0, C1 there "
         "(3 minutes each, `--skip 10`); measure link speed on that WiFi and set R = 0.9 x downlink;", "SESSION_LOG.md")
H.write_text(h); R.write_text(r); L.write_text(l)
print("Done. Review: git diff --stat")
