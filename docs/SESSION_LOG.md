# Session Log

One entry per working session, oldest first. This file answers three questions: what was done, what proves it, and what the next session must check first.

## How to use this file

**Start of every session (2 minutes):**
1. Run `./tools/session_start_check.sh` in the project root (add `--full` once per milestone to also run the tests and the build).
2. Compare its output with the **Handoff state** of the last entry below. Every difference (uncommitted files, missing tag, different HEAD, unexpected data) goes into the new entry under **Entry check**.
3. Only then start the planned work.

**End of every session (5 minutes):** add a new `## Session N` block at the bottom (copy the template) and commit it together with the work (`git add -A && git commit`).

**Rules**
- An item is **Done** only with evidence: a commit hash, a test result, a file path, or pasted terminal output. Without evidence it is written as *claimed, not verified*.
- A session ends with a clean tree (everything committed) and the HEAD hash written down. Work that is not committed does not count as done.
- Numbers from the phone always name the file or label they came from.
- Never edit an old entry to change history; if something turns out wrong, record the correction in the new entry.

**Template**

```
## Session N: <date>, <short title>
**Goal:** one sentence.
**Entry check:** what the start-of-session check showed; differences from the last entry.
**Done (with evidence):**
- item: evidence
**Claimed, not verified:**
- item
**Decisions:** what was decided and why.
**Problems:** what broke, cause (or hypothesis), status.
**Handoff state:**
- HEAD at end: <hash>
- Tags: ...
- Tree clean: yes/no
**Open at end:**
- item
**Next session, first steps:**
1. item
```

---

## Session 1: 7 Oct 2026, setup and build
*Reconstructed on 14 Oct 2026 from git history and the handoff report.*
**Goal:** get the upstream project (NetValve) building and running on the phone.
**Done (with evidence):**
- Tools installed, upstream forked and pinned: tag `pinned-base` (`f7b34b0`).
- Missing manifest and resources restored, scripts made executable: `a482a91`.
- Network engine (netstack AAR) built, macOS fix in `build-aar.sh`: `ea5f537`, tag `week1-aar-built`.
- APK installed and started on the OnePlus Nord CE5 (handoff Section 7).
**Handoff state:** HEAD at end: ea5f537 · Tags: pinned-base, week1-aar-built.
**Open at end:** usage-access permission bug, VPN loop (`protect()`), app icons.

## Session 2: 7 to 12 Oct 2026, first fix round
*Reconstructed.*
**Goal:** fix the problems found on the phone and write the first handoff report.
**Done (with evidence):**
- Usage-access permission, `protect()`, navigation, app icons fixed; reported working by the student: `511c0de` (committed 12 Oct, 02:47).
**Problems:** the first fix round was built on 7 Oct but committed only on 12 Oct (work sat uncommitted for days).
**Handoff state:** HEAD at end: 511c0de.

## Session 3: 12 Oct 2026, scheduler core, simulation, M0
*Reconstructed.*
**Goal:** write and verify the scheduler algorithm; adopt the cleaned package.
**Done (with evidence):**
- Scheduler core in `app/src/main/kotlin/dev/netvalve/scheduler/` (Mode B, Mode A rule, calibration maths, coroutine adapter, tier rules) with 36 unit tests (13 + 5 + 6 + 5 + 7 in five test files); simulation of C1 to C5 with results in `docs/data/sim_results_table.txt`: `3df8724`, tag `scheduler-core`.
- M0 closed: 36 tests pass under Gradle on the Mac (per handoff Section 10), package adopted at `~/Downloads/qos-priority-scheduler`: `5e49d1c`, `40ec79d`.
**Decisions:** work is ordered by dependency (milestones M0 to M8), not by calendar weeks.
**Handoff state:** HEAD at end: 40ec79d · Tags: scheduler-core.

## Session 4: 13 Oct 2026, PayProbe code (M1, code only)
*Reconstructed.*
**Goal:** build the measuring app.
**Done (with evidence):**
- Module `payprobe/` (8 source files, about 650 lines), 9 JVM tests, `analysis/summarize.py` (about 100 lines); builds with Gradle.
- First launch on the phone crashed (insets controller used before `setContentView`); fixed (handoff Section 8, problem 12).
**Problems:** none of this was committed until 14 Oct (same gap as Session 2).
**Handoff state:** uncommitted at end of session.

## Session 5: 14 Oct 2026, M1 accepted on the phone, first idle runs, docs reconciled
**Goal:** run PayProbe on the phone, record idle baselines, bring the docs in line with the code, set up this log.
**Entry check:** docs said "M1 phone check pending"; the code and the phone were already past that. The commit tagged `m2-idle-baselines-mobile` (`4e1b6da`) did not contain the M2 docs update.
**Done (with evidence):**
- M1 accepted on the phone: PayProbe runs, writes CSVs, `adb pull` works, `summarize.py` reads them (files now in `docs/data/mobile_2026-10-14/`).
- Three VPN-on idle runs on mobile data: p50 200 to 208 ms, p95 246 to 350 ms, so the noise floor is about 100 ms at p95 (handoff Section 10.1).
- Docs reconciled with the code: `c9c102b` (handoff v2.1, roadmap tracker, README; stale top-level `PROJECT_HANDOFF.md` removed; mobile CSVs archived).
**Claimed, not verified:** the live chart in PayProbe moved during a run (no screenshot recorded). The docs other than the three updated ones (`ARCHITECTURE`, `LIMITATIONS`, `THROTTLING`, and so on) were not reviewed.
**Decisions:** redo the idle baselines (C1, C0, C1) on the network the load runs will use; keep all earlier mobile data as noise-floor evidence; use this log and the start-of-session check from now on.
**Problems:** (13) first probes after Start sometimes time out (hypothesis: tunnel not ready); (14) the VPN-off (C0) run does not exist in the data, two C1 files had been reported as C0 (handoff Section 8).
**Handoff state:**
- HEAD at end: c9c102b (the commit that adds this log follows it)
- Tags: pinned-base, week1-aar-built, scheduler-core, m2-idle-baselines-mobile (m1-payprobe-done to be added)
- Tree clean: yes at c9c102b
**Open at end:**
- Redo idle runs C1, C0, C1 and read the VPN overhead from them (M2).
- Tag `m1-payprobe-done`; push branch `qos-scheduler` to GitHub (not pushed yet; only upstream `main` is there).
- Decide what "old project removal" means for the GitHub fork (default branch change versus deleting anything).
- Review the untouched docs for stale statements.
- M2 rest: speed test and R, load runs (a) no control, (b) static cap, at least 5 repeats each, decision GO or No-Go written in the handoff.
- M3 to M8 not started: the scheduler is not connected to the relay (the four `throttleManager.pace` call sites in `FlowSupervisor.kt` are untouched), there is no tier UI, no traffic simulator.
**Next session, first steps:**
1. Run `./tools/session_start_check.sh` and write the Entry check.
2. Paste the idle-run table (C1, C0, C1) and decide whether VPN overhead is measurable.
3. Move on to M2: speed test, R, load runs.
