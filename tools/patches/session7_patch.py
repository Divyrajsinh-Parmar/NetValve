#!/usr/bin/env python3
"""Session 7 docs patch (14 Oct 2026).

Run from the project root:   python3 ~/Downloads/session7_patch.py
What it does (nothing is deleted; review with `git diff` afterwards):
  1. docs/PROJECT_HANDOFF.md  - Section 6 commit rows, problem 13, --skip 10 rule, change log row
  2. docs/IMPLEMENTATION_ROADMAP.md - M1 acceptance caveat
  3. docs/SESSION_LOG.md      - appends the Session 7 entry
It checks every text it replaces occurs exactly once; if anything does not match, it
stops and writes NOTHING. Running it a second time does nothing.
"""
import sys
from pathlib import Path

HANDOFF = Path("docs/PROJECT_HANDOFF.md")
ROADMAP = Path("docs/IMPLEMENTATION_ROADMAP.md")
LOG = Path("docs/SESSION_LOG.md")

for p in (HANDOFF, ROADMAP, LOG):
    if not p.is_file():
        sys.exit(f"STOP: {p} not found. Run this from ~/Downloads/qos-priority-scheduler")

handoff = HANDOFF.read_text()
roadmap = ROADMAP.read_text()
log = LOG.read_text()

if "## Session 7:" in log:
    sys.exit("Session 7 entry is already in docs/SESSION_LOG.md. Nothing changed.")


def replace_once(text, old, new, where):
    n = text.count(old)
    if n != 1:
        sys.exit(f"STOP: expected 1 match in {where}, found {n} for: {old[:70]}...  Nothing was written.")
    return text.replace(old, new)


# ---------- PROJECT_HANDOFF.md ----------
handoff = replace_once(
    handoff,
    "| **next commit** | WiFi idle baselines (C1, C0, C1), `finish_session.sh`, Session 6 entry |",
    "| `fb03ca0` | `tools/finish_session.sh` added |\n"
    "| `478f4cd` | WiFi idle baselines (C1, C0, C1), Session 6 entry; pushed to GitHub |",
    "PROJECT_HANDOFF.md Section 6",
)
handoff = replace_once(
    handoff,
    "| `c9c102b` | docs brought in line with the code; mobile idle runs archived |",
    "| `c9c102b` | docs brought in line with the code; mobile idle runs archived (tag `m1-payprobe-done` points here, "
    "not at the PayProbe code commit `4e1b6da`) |",
    "PROJECT_HANDOFF.md Section 6",
)
handoff = replace_once(
    handoff,
    "open; not seen in the three WiFi runs (0 failures after the first 10 samples; the first 10 samples were not inspected) |",
    "open; **checked 14 Oct 2026 (Session 7):** the first 10 samples of all three WiFi runs are `ok=1` (no timeouts); "
    "the only timeouts in the repo are the two 3-row mobile files, so the cause (tunnel not ready) is still a hypothesis |",
    "PROJECT_HANDOFF.md Section 8",
)
handoff = replace_once(
    handoff,
    "they are kept out of future analysis. These runs live",
    "they are kept out of future analysis (always run `summarize.py` with `--skip 10`: with `--by label` and no skip "
    "the two files are pooled into `C1_idle_mobile_r1`, giving 384 rows, 3 failures and max 5000 ms; checked 14 Oct 2026). "
    "These runs live",
    "PROJECT_HANDOFF.md Section 10.1",
)
last_row = ("| 14 Oct 2026 | **Idle baselines on WiFi (C1, C0, C1) recorded:** no measurable VPN overhead at idle; "
            "WiFi p95 noise about 66 ms; problem 14 closed | 8, 10, 11 |")
handoff = replace_once(
    handoff,
    last_row,
    last_row + "\n"
    "| 14 Oct 2026 | Session 7 entry check: tables 10.1 and 10.2 recomputed from the CSVs (they match); problem 13 "
    "first-10-samples check; `--skip 10` rule; Section 6 commit rows corrected | 6, 8, 10 |",
    "PROJECT_HANDOFF.md Section 15",
)

# ---------- IMPLEMENTATION_ROADMAP.md ----------
roadmap = replace_once(
    roadmap,
    "The system DNS cache still applies (as for any app), so after the first probe the DNS part is small.",
    "The system DNS cache still applies (as for any app), so after the first probe the DNS part is small. "
    "**Acceptance caveat (Session 7, 14 Oct 2026):** this milestone asked for a 5-minute run; the longest file in the repo "
    "is 243 s and the idle runs are 180 to 196 s. Whether the on-screen p50 and p95 matched the CSV, and whether the chart "
    "moved, was not recorded.",
    "IMPLEMENTATION_ROADMAP.md M1 status",
)

# ---------- SESSION_LOG.md ----------
entry = """
## Session 7: 14 Oct 2026, entry check, data re-verified, start of the M2 load experiment
**Goal:** reconcile the start-of-session check with Session 6, then start M2 (WiFi link speed, R = 0.9 x downlink, load runs).
**Entry check:** `./tools/session_start_check.sh` (quick mode) gave 18 ok, 2 warnings. HEAD `478f4cd`, tree clean, `origin/qos-scheduler` equals HEAD (this compares the local tracking ref; it is not a live query of GitHub). The last logged commit `c34fc5f` is in the history; two commits came after it: `fb03ca0` (finish tool) and `478f4cd` (WiFi runs and the Session 6 entry). Both warnings are the two 3-row files `C1_idle_mobile_r0_20261014_122143.csv` and `C1_idle_mobile_r1_20261014_122201.csv`: each holds three 5000 ms `timeout` rows over about 10 s. They were already described in Session 5 and handoff 10.1, so there is no new warning. Section 7 (tests and builds) is absent from the output because `--full` was not used.
**Done (with evidence):** (checked by the AI on a copy of the zip; the student's Mac and phone were not observed)
- Handoff tables 10.1 and 10.2 recomputed from the CSVs with `analysis/summarize.py --skip 10`: every n, p50, p95, p99, max and mean matches.
- Problem 13, first 10 samples: in all three WiFi files they are `ok=1` (first sample 393, 241 and 306 ms). The only timeouts in the repo are in the two 3-row mobile files.
- Test counts: 36 `@Test` in the five scheduler test files (13 + 5 + 6 + 5 + 7) and 9 in `PayProbeCoreTest.kt`.
- `FlowSupervisor.kt` does not use the scheduler; its four `throttleManager.pace` calls are at lines 95, 109, 180 and 190 (M3 not started).
- Git: `origin/qos-scheduler` = `478f4cd`, `origin/main` = `f7b34b0` (main untouched); 18 commits up to `c34fc5f`, as Session 6 recorded.
- `tools/finish_session.sh` tested on a copy with a local bare remote: nothing changed gives exit 0; code without the session log gives STOP; code and log without the handoff gives STOP; all three together commit and push. Limit: it checks that the docs files are part of the change, not that their content is right.
**Claimed, not verified:**
- Setup of the three WiFi runs (only PayProbe ticked, mobile data off, VPN key icon gone for C0): not visible in the CSVs.
- Which WiFi the runs used (own router, hotspot or college) and its name, band and signal: not recorded.
- M1 acceptance: the roadmap asked for a 5-minute run; the longest file is 243 s (`payprobe_test_20261014_115708.csv`), the idle runs are 180 to 196 s. The on-screen p50 and p95 versus the CSV, and the moving chart, were not recorded.
- Test passes: the last Gradle test output is from Session 3 (36 tests, M0); the 9 PayProbe tests were run in a sandbox on 12 Oct; no `--full` run since.
- Problem 8 (`protect()` fix) rests on the student's report only. The Session 6 GitHub screenshot is not in the repo and the remote tags were not checked.
**Decisions:** none new.
**Problems:** (a) `summarize.py --by label` without `--skip 10` pools the two 3-row files into `C1_idle_mobile_r1` (384 rows, 3 failures, max 5000 ms); rule added to handoff 10.1. (b) Handoff Section 6 still said "next commit" for `478f4cd`; corrected. (c) Tag `m1-payprobe-done` points to `c9c102b` (docs commit), the PayProbe code is `4e1b6da`; recorded, not an error.
**Handoff state:**
- HEAD at end: 478f4cd (the commit that adds this entry follows it)
- Tags: pinned-base, week1-aar-built, scheduler-core, m1-payprobe-done, m2-idle-baselines-mobile
- Tree clean: after `./tools/finish_session.sh`
**Open at end:**
- M2: record WiFi name, band and signal; measure link speed on that WiFi and set R = 0.9 x downlink; choose and test the load source (3 to 4 parallel downloads); load runs (a) no control and (b) static cap on the downloader, at least 5 repeats each; write the GO or No-Go decision in the handoff.
- Run `./tools/session_start_check.sh --full` once to get Gradle test and build evidence for M0 and M1.
- Decide what "old project removal" means for the GitHub fork (default branch versus deleting anything); nothing was deleted.
- Review the untouched docs (`ARCHITECTURE`, `LIMITATIONS`, `THROTTLING` and so on) for stale statements.
- M3 to M8 not started: the scheduler is not connected to the relay.
**Next session, first steps:**
1. Run `./tools/session_start_check.sh` and write the Entry check.
2. Continue M2 from the first item under Open at end.
"""
if not log.endswith("\n"):
    log += "\n"
log += entry

# ---------- write everything only now ----------
HANDOFF.write_text(handoff)
ROADMAP.write_text(roadmap)
LOG.write_text(log)
print("Done. Review with: git diff --stat   and   git diff docs/PROJECT_HANDOFF.md")
