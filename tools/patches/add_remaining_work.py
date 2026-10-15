#!/usr/bin/env python3
"""Puts REMAINING_WORK.md into docs/ and records it in SESSION_LOG.md and PROJECT_HANDOFF.md.

Save this script and REMAINING_WORK.md in the same folder (for example ~/Downloads), then from the
project root, AFTER session8_patch.py:
    python3 ~/Downloads/add_remaining_work.py
Each edited text must occur exactly once or nothing is written. Running it twice does nothing.
"""
import sys
from pathlib import Path

src = Path(__file__).resolve().parent / "REMAINING_WORK.md"
dst = Path("docs/REMAINING_WORK.md")
H, L = Path("docs/PROJECT_HANDOFF.md"), Path("docs/SESSION_LOG.md")

if not src.is_file():
    sys.exit(f"STOP: {src} not found. Keep REMAINING_WORK.md next to this script.")
for p in (H, L):
    if not p.is_file():
        sys.exit(f"STOP: {p} not found. Run from ~/Downloads/qos-priority-scheduler")
if dst.exists():
    sys.exit("docs/REMAINING_WORK.md already exists. Nothing changed.")

h, l = H.read_text(), L.read_text()
if "## Session 8:" not in l:
    sys.exit("STOP: run session8_patch.py first. Nothing written.")


def once(t, old, new, where):
    n = t.count(old)
    if n != 1:
        sys.exit(f"STOP: expected 1 match in {where}, found {n}: {old[:70]}... Nothing written.")
    return t.replace(old, new)


l = once(l,
    "- Code written for M3, M4 and M5 as listed in handoff Section 9.1 (new and changed files).\n",
    "- Code written for M3, M4 and M5 as listed in handoff Section 9.1 (new and changed files).\n"
    "- `docs/REMAINING_WORK.md` added: built versus not built, remaining work in order, which reports to update after each stage, and ready prompts for the next sessions.\n",
    "SESSION_LOG.md Session 8")
row = "| 14 Oct 2026 | Plan change: M3, M4, M5 code written before M2 and M6 testing; Section 9.1 added; problem 15 (own UID never controlled) added | 1, 8, 9, 11 |"
h = once(h, row, row + "\n| 14 Oct 2026 | `docs/REMAINING_WORK.md` added (status, remaining work, report update list, prompts) | 15 |",
         "PROJECT_HANDOFF.md Section 15")

dst.write_text(src.read_text())
H.write_text(h)
L.write_text(l)
print("Done. docs/REMAINING_WORK.md created. Review: git status")
