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

## Session 6: 14 Oct 2026, GitHub push, WiFi idle baselines, finish tool
**Goal:** put the work on GitHub, redo the idle baselines (C1, C0, C1) on WiFi with a real VPN-off run, and make sure every change updates the docs and GitHub.
**Entry check:** HEAD was `c34fc5f` (14 ok, 4 warnings: branch not on GitHub, two tiny CSVs, no C0 run). GitHub afterwards showed the same commit `c34fc5f`, 18 commits, 5 tags (screenshot). The start-of-session check was not re-run after the push.
**Done (with evidence):**
- Branch `qos-scheduler` and 5 tags pushed to `Divyrajsinh-Parmar/NetValve` (terminal output: new branch, 5 new tags; GitHub page shows `c34fc5f`). `main` untouched.
- Idle baselines on WiFi, three runs of about 3 minutes (3 CSVs in `docs/data/idle_wifi/files/`, listed on the phone and summarised by `analysis/summarize.py`): C1 r1 p50 193 / p95 334; C0 r1 p50 214 / p95 276; C1 r2 p50 202 / p95 268 (handoff Section 10.2). Result: no measurable VPN overhead at idle; WiFi p95 noise about 66 ms.
- Problem 14 (missing C0 run) closed with `C0_idle_wifi_r1`.
- `tools/finish_session.sh`: commit and push in one command, refuses if the session log or (for code changes) the handoff is not part of the change; tested against a local test remote.
**Claimed, not verified:** the exact setup of the three runs (only PayProbe ticked, mobile data off, VPN key icon gone for C0) was instructed but not confirmed in the chat; the first 10 samples of each run (skipped by the summary) were not inspected for timeouts.
**Decisions:** VPN overhead is only stated as "not measurable" until M6 gives at least 5 runs per condition; load runs will use the same WiFi.
**Problems:** problem 13 (first probes timing out) not seen again after the first 10 samples; stays open until the first samples are checked.
**Handoff state:**
- HEAD at end: c34fc5f (the commit that adds this entry and the WiFi data follows it)
- Tags: pinned-base, week1-aar-built, scheduler-core, m1-payprobe-done, m2-idle-baselines-mobile
- Tree clean: after `./tools/finish_session.sh`
**Open at end:**
- M2: measure link speed, set R = 0.9 x downlink; choose and test the load source (3 to 4 parallel downloads); load runs (a) no control and (b) static cap on the downloader, at least 5 repeats each; write the GO or No-Go decision in the handoff.
- Record WiFi name, band and signal for the load runs.
- Decide what "old project removal" means for the GitHub fork (default branch versus deleting anything); nothing was deleted.
- Review the untouched docs (`ARCHITECTURE`, `LIMITATIONS`, `THROTTLING` and so on) for stale statements.
- M3 to M8 not started: the scheduler is not connected to the relay.
**Next session, first steps:**
1. Run `./tools/session_start_check.sh` and write the Entry check.
2. Link speed test on the same WiFi, then set R.
3. Pick the load source and do one trial load run before the 5 repeats.

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
- Phone check (student's terminal output): `adb shell cmd wifi status` printed "Wifi is disabled" (so no SSID, band or signal yet); `pm list packages` shows `com.termux`, so Termux is available as the load source. Its installer and version are not checked yet.
**Claimed, not verified:**
- Setup of the three WiFi runs (only PayProbe ticked, mobile data off, VPN key icon gone for C0): not visible in the CSVs.
- Which WiFi the runs used (own router, hotspot or college) and its name, band and signal: not recorded; the student cannot remember (asked 14 Oct 2026). Treated as network unknown.
- M1 acceptance: the roadmap asked for a 5-minute run; the longest file is 243 s (`payprobe_test_20261014_115708.csv`), the idle runs are 180 to 196 s. The on-screen p50 and p95 versus the CSV, and the moving chart, were not recorded.
- Test passes: the last Gradle test output is from Session 3 (36 tests, M0); the 9 PayProbe tests were run in a sandbox on 12 Oct; no `--full` run since.
- Problem 8 (`protect()` fix) rests on the student's report only. The Session 6 GitHub screenshot is not in the repo and the remote tags were not checked.
**Decisions:** the WiFi idle runs are kept as evidence but labelled network unknown; idle baselines (C1, C0, C1) are repeated on the network chosen for the load runs. Load source: Termux with parallel `curl` jobs.
**Problems:** (a) `summarize.py --by label` without `--skip 10` pools the two 3-row files into `C1_idle_mobile_r1` (384 rows, 3 failures, max 5000 ms); rule added to handoff 10.1. (b) Handoff Section 6 still said "next commit" for `478f4cd`; corrected. (c) Tag `m1-payprobe-done` points to `c9c102b` (docs commit), the PayProbe code is `4e1b6da`; recorded, not an error.
**Handoff state:**
- HEAD at end: 478f4cd (the commit that adds this entry follows it)
- Tags: pinned-base, week1-aar-built, scheduler-core, m1-payprobe-done, m2-idle-baselines-mobile
- Tree clean: after `./tools/finish_session.sh`
**Open at end:**
- M2: choose the load network (own router or hotspot), turn WiFi on and record name, band and signal; repeat the idle baselines C1, C0, C1 there (3 minutes each, `--skip 10`); measure link speed on that WiFi and set R = 0.9 x downlink; choose and test the load source (3 to 4 parallel downloads); load runs (a) no control and (b) static cap on the downloader, at least 5 repeats each; write the GO or No-Go decision in the handoff.
- Run `./tools/session_start_check.sh --full` once to get Gradle test and build evidence for M0 and M1.
- Decide what "old project removal" means for the GitHub fork (default branch versus deleting anything); nothing was deleted.
- Review the untouched docs (`ARCHITECTURE`, `LIMITATIONS`, `THROTTLING` and so on) for stale statements.
- M3 to M8 not started: the scheduler is not connected to the relay.
**Next session, first steps:**
1. Run `./tools/session_start_check.sh` and write the Entry check.
2. Continue M2 from the first item under Open at end.

## Session 8: 14 Oct 2026, plan change and M3 to M5 code written
**Goal:** the student asked to change the plan: build all infrastructure first (M3 wiring, M4 tier UI, M5 simulator), test and fix afterwards, because only a few days are left.
**Decisions:** (a) plan change accepted with the risk written down (roadmap Section 1 and 8, handoff Section 9.1). (b) Own UID counts as controlled only when NetValve is ticked in ONLY_SELECTED mode (handoff problem 15). (c) One `SchedulerController` object is given to `FlowSupervisor`. (d) UDP keeps `DROP_OLDEST`; UDP picks the scheduler on or off when a flow opens. (e) Simulator uses raw HTTP/1.1 sockets so every connection is separate.
**Done (with evidence), checked by the AI in a sandbox on a copy of the project; the student's Mac and phone were not observed:**
- Pure Kotlin (`scheduler/`, `sim/`) compiled with kotlinc 2.0.21 and warnings as errors.
- 62 JVM tests pass in the sandbox with a stand-in test runner: the 36 old scheduler tests plus 26 new ones (`RateUnitsTest` 6, `TierResolverTest` 6, `SchedulerControllerTest` 6, `TrafficSimulatorTest` 8). Not run with Gradle.
- Code written for M3, M4 and M5 as listed in handoff Section 9.1 (new and changed files).
- `docs/REMAINING_WORK.md` added: built versus not built, remaining work in order, which reports to update after each stage, and ready prompts for the next sessions.
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

## Session 9: 15 Oct 2026, entry check and the first full Gradle check
**Goal:** run `./tools/session_start_check.sh --full` after the M3 to M5 code, and fix what fails.
**Entry check:** student's Mac output: 17 ok, 3 warnings. HEAD `a973dab` ("Session 8: add REMAINING_WORK.md"), tree clean, `origin/qos-scheduler` equals HEAD (local tracking ref, not a live query), last logged commit `478f4cd` in history, tags present. This shows steps 6 and 8 of Session 8 were done. Warnings: the two 3-row mobile files (known since Session 5, handoff 10.1) and `./gradlew test FAILED` (new). No phone was connected, so section 6 had nothing to check.
**Done (with evidence):**
- `:app:assembleDebug -Pnetvalve.netstack=true` and `:payprobe:assembleDebug` pass on the Mac ("both APKs build"): the M3 to M5 Android code compiles under Gradle, including Hilt, DataStore and Compose. The check runs both with `-q`, so warnings are hidden.
- Failure found: `./gradlew test` stops at `:payprobe:compileDebugUnitTestKotlin`. `PayProbeCoreTest.kt` line 3 imports `com.sun.net.httpserver.HttpServer`, which is unresolved on the Android unit-test classpath. The error is in an M1 test, not in the new code.
- Fix written in an AI sandbox: the test server now uses `java.net.ServerSocket` (`TinyHttpServer` at the end of `PayProbeCoreTest.kt`). 9 of 9 tests pass in the sandbox in 3 runs with a stand-in runner; the old version passed 9 of 9 there too, so the sandbox could not show the Gradle problem. Gradle result: see the next bullets.
- Mac output after that fix: `:payprobe` tests now compile under Gradle. 9 tests ran, 8 passed, 1 failed in task `:payprobe:testReleaseUnitTest`: `hungServerIsCutOffAtTheTimeoutAndStoredAsTheCap`, `ComparisonFailure` at `PayProbeCoreTest.kt:106`, the line that compares the stored note with "timeout". Gradle stopped there, so the app's unit tests did not run. The compiler warnings in the output (`DnsCache.kt`, `DeviceStateMonitorImpl.kt`, `LogsScreen.kt`, `Navigation.kt:52`) are not from the M3 to M5 code.
- Hypothesis (not confirmed, the actual note was not in the output): the socket read timeout fired just before the 400 ms hard cap, so the note was the exception name instead of "timeout". In the sandbox the same test returned "timeout" 40 of 40 times, so the race could not be reproduced there.
- Fix written in the sandbox: `Prober` stores `SocketTimeoutException` as note "timeout" at the cap (new `noteFor`), a new test `everyKindOfTimeoutIsNotedAsTimeout`, and the assertions of the failing test now print the actual note, latency and wall time. 10 of 10 pass in the sandbox in 3 runs with a stand-in runner. Gradle result of this fix: not yet seen.
**Claimed, not verified:**
- That all PayProbe tests pass under Gradle: 8 of 9 passed on the Mac on 15 Oct (see above), the fix for the 9th is untested there. Earlier notes ("9 JVM tests" for M1) came from a sandbox; they had never run under Gradle.
- Whether the app's unit tests (36 old and 26 new) compile and pass under Gradle: Gradle stopped at the PayProbe failure, so the output says nothing about them.
- Everything on the phone (smoke checks) and all M2 and M6 measurements.
**Decisions:** none new.
**Problems:** handoff problem 16 added; Section 1, 9.1, 11, the roadmap tracker rows M3 to M5 and `REMAINING_WORK.md` now say "debug build passes" instead of "not built".
**Handoff state:**
- HEAD at end: a973dab (the commit that adds this entry follows it)
- Tags: pinned-base, week1-aar-built, scheduler-core, m1-payprobe-done, m2-idle-baselines-mobile
- Tree clean: after `./tools/finish_session.sh`
**Open at end:**
- Run `./gradlew test --continue` (so the app's unit tests also run after a PayProbe failure). Paste the summary and any failures.
- Phone smoke checks (stop the VPN, install, Mode OFF like before; tier chips and persistence; tick NetValve, restart tunnel, simulator shows traffic), then M2 and M6 on the hotspot.
- Cloudflare `__up` check; smaller upstream socket buffers not done; "old project removal" decision; review untouched docs.
- M6 to M8 not started.
**Next session, first steps:**
1. Run `./tools/session_start_check.sh --full` and write the Entry check.
2. Fix whatever still fails in `./gradlew test`, then the phone checks.
