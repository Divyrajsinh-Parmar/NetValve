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
**Open at end (status update below, 15 Oct 2026):**
- M2: choose the load network (own router or hotspot), turn WiFi on and record name, band and signal; repeat the idle baselines C1, C0, C1 there (3 minutes each, `--skip 10`); measure link speed on that WiFi and set R = 0.9 x downlink; choose and test the load source (3 to 4 parallel downloads); load runs (a) no control and (b) static cap on the downloader, at least 5 repeats each; write the GO or No-Go decision in the handoff.
- Run `./tools/session_start_check.sh --full` once to get Gradle test and build evidence for M0 and M1.
- Decide what "old project removal" means for the GitHub fork (default branch versus deleting anything); nothing was deleted.
- Review the untouched docs (`ARCHITECTURE`, `LIMITATIONS`, `THROTTLING` and so on) for stale statements.
- M3 to M8 not started: the scheduler is not connected to the relay.
**Next session, first steps (superseded by the status update below):**
1. Run `./tools/session_start_check.sh` and write the Entry check.
2. Continue M2 from the first item under Open at end.

**Status update (15 Oct 2026, written in Session 9; the text above is left as written on 14 Oct):**
- Resolved: "`FlowSupervisor.kt` does not use the scheduler (M3 not started)". The scheduler hooks were written in Session 8 and the debug builds of both APKs pass on the Mac (Session 9 check output). The code has not run on the phone.
- Resolved: "no `--full` run since" and "Test passes: last Gradle output is from Session 3". `--full` ran in Session 9. The first `./gradlew test` failed because the 9 PayProbe tests did not compile under Gradle (handoff problem 16), the second run found the simulator stop race (problem 17), and the third run, `./gradlew test --continue`, ended with BUILD SUCCESSFUL. So the earlier "9 tests" for M1 were sandbox results only; they pass under Gradle since 15 Oct 2026.
- Superseded: the M2 list under Open at end. After the plan change (Session 8) the order is: phone smoke checks first, then M2 and M6 together on the hotspot network (see `docs/REMAINING_WORK.md`).
- Still open, unchanged: the network of the three WiFi idle runs is unknown and their setup is not visible in the CSVs; the M1 acceptance items (5-minute run, on-screen p50 and p95 versus the CSV, moving chart); problem 8 (`protect()` fix) rests on the student's report; the Session 6 GitHub screenshot and the remote tags were not checked; the installer and version of Termux; the hotspot details (model, 4G or 5G, bars, data pack); the "old project removal" decision; the review of the untouched docs; M6 to M8 not started.
- The current list of what is left is the Open at end of the newest session entry and `docs/REMAINING_WORK.md`.

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
- Fix written in the sandbox: `Prober` stores `SocketTimeoutException` as note "timeout" at the cap (new `noteFor`), a new test `everyKindOfTimeoutIsNotedAsTimeout`, and the assertions of the failing test now print the actual note, latency and wall time. 10 of 10 pass in the sandbox in 3 runs with a stand-in runner. Gradle result of this fix: see the next bullets.
- Second Mac run (`./gradlew test --continue`): the output shows no PayProbe test failure; the only failing task is `:app:testDebugUnitTest`: 96 tests ran, 95 passed, 1 failed: `TrafficSimulatorTest.downloadUsesThreeParallelConnectionsAndCountsBytes` with `NoSuchElementException` at `TrafficSimulatorTest.kt:140`, the `sim.stop()` line.
- Cause (likely, the stack trace was not in the output): `TrafficSimulator.stopInternal` used `sockets.toList()` on a concurrent set while workers removed their sockets. A sandbox demo with the same Kotlin call gave 38,489 `NoSuchElementException` in 2 million tries, none with a plain `for` loop. The race could not be reproduced inside the simulator in the sandbox (400 start/stop rounds, 3 times, no failure).
- Fix written in the sandbox: plain `for` loop in `stopInternal` and a new stress test `repeatedStartAndStopNeverThrows` (100 start/stop rounds). 63 of 63 pure tests pass in the sandbox in 3 runs (old 36, new 27) with the stand-in runner. Gradle result of this fix: third run, 15 Oct 2026, `./gradlew test --continue` on the Mac ended with `BUILD SUCCESSFUL in 1m 11s`. The pasted output showed only the end (compiler warnings from old files, a Gradle daemon metaspace warning and the BUILD line), so the exact test counts were not seen.
- The one-time patch scripts and code patches of Sessions 7 to 9 are archived in `tools/patches/` (`README.md` there gives the order and checksums). They are already applied and stop by themselves if run again. A replay of the whole chain was done only in an AI sandbox copy of the 14 Oct zip, not on the student's Mac.
**Claimed, not verified:**
- That all PayProbe tests pass under Gradle: 8 of 9 passed on the Mac on 15 Oct (see above), the fix for the 9th is untested there. Earlier notes ("9 JVM tests" for M1) came from a sandbox; they had never run under Gradle.
- The exact number of tests in the final Gradle run (not in the pasted output). The warning that the Gradle daemon ran out of metaspace did not fail the build; its limit is 1g in `gradle.properties` (not changed).
- Everything on the phone (smoke checks) and all M2 and M6 measurements.
**Decisions:** none new.
**Problems:** handoff problem 16 added; Section 1, 9.1, 11, the roadmap tracker rows M3 to M5 and `REMAINING_WORK.md` now say "debug build passes" instead of "not built".
**Handoff state:**
- HEAD at end: a973dab (the commit that adds this entry follows it)
- Tags: pinned-base, week1-aar-built, scheduler-core, m1-payprobe-done, m2-idle-baselines-mobile
- Tree clean: after `./tools/finish_session.sh`
**Open at end:**
- Build and unit tests are green (15 Oct 2026). Optional: raise the Gradle metaspace limit (not done, not tested).
- Phone smoke checks (stop the VPN, install, Mode OFF like before; tier chips and persistence; tick NetValve, restart tunnel, simulator shows traffic), then M2 and M6 on the hotspot.
- Cloudflare `__up` check; smaller upstream socket buffers not done; "old project removal" decision; review untouched docs.
- M6 to M8 not started.
**Next session, first steps:**
1. Run `./tools/session_start_check.sh --full` and write the Entry check.
2. Phone smoke checks (see Open at end), then M2 and M6 on the hotspot.

## Session 10: 15 to 16 Oct 2026, first phone smoke check
**Goal:** put the new APKs on the phone and check the first things by hand (REMAINING_WORK item 2).
**Entry check:** not re-run in this part of the session; the last full check output is in Session 9.
**Done (reported by the student in the chat; no terminal output, screenshot or file for these):**
- The student chose a fresh install (uninstall first, so saved settings and tiers are reset) and reported that the new versions of both apps (`dev.netvalve.debug`, `dev.netvalve.payprobe`) are on the phone. The outputs of the CSV backup, the uninstall and the install commands were not shown here, so it is not known whether the CSV backup (`~/Desktop/payprobe_phone_backup`) was made.
- First open of NetValve: the Dashboard scheduler card was OFF with R = 0; the Apps list showed NetValve and PayProbe; ticking PayProbe showed the fixed High chip; Start brought the VPN permission popup, then the key icon; the browser worked with the tunnel on.
- Second part, 16 Oct 2026, from the student's screenshots (`SMOKE_TEST.pdf`, not in the repo) and terminal output: three PayProbe runs through the tunnel with the scheduler OFF and PayProbe ticked, 0 failures in each. Labels as stored: `SMOKE_off__mobile_r1` (mobile data, 5G icon, no WiFi icon), `SMOKE_off__Wifi_r1` (WiFi named "GUEST" in the quick-settings screenshot; the student says it is the college WiFi), `SMOKE_off__hotspot_r1` (the test phone itself was the hotspot host; the quick-settings screenshot shows 1 device connected at 3:44 pm). The labels have a double underscore because the student typed the angle brackets of the template and the app replaced them. CSVs: `docs/data/smoke_2026-10-16/`.
- `summarize.py --skip 10` (n, p50, p95, p99, max, mean in ms): mobile 81, 242, 292, 458, 458, 246; WiFi 81, 117, 324, 473, 473, 133; hotspot 82, 223, 259, 347, 347, 221. On-screen numbers after Stop (all samples): mobile n=91, p50 243, p95 318, p99 500, max 500, mean 253; WiFi n=91, 115, 324, 473, 473, 130; hotspot n=92, 222, 265, 379, 379, 220. The sample counts agree (91 = 81 + 10, 92 = 82 + 10). The percentiles cannot be compared yet, because the screen uses all samples and the table skips 10; `--skip 0` is the exact check.
- Settings screenshots (3:38 pm, before the mobile run): Dashboard shows "Protected", the scheduler switch OFF, R = 0 and 0, one controlled app (PayProbe); the Apps list shows PayProbe ticked with the fixed High chip and the other apps labelled "Not controlled (bypasses the scheduler)". The quick-settings screenshots show the VPN pill at 3:41 pm and 3:44 pm.
**Claimed, not verified:**
- Everything above (student-reported only), except the numbers and screenshots listed in the second part.
- That PayProbe traffic really went through the tunnel: NetValve showed 0 B/s and 0 active flows at 3:38 pm when no probe was running, and there is no screenshot of NetValve during a run.
- That the chart moves live (the screenshots are taken after Stop). The scheduler state during the WiFi and hotspot runs (not shown again; the student says it stayed OFF) and the VPN at 3:46 pm (no key icon visible in that status bar; the VPN pill at 3:44 pm shows it was on two minutes earlier).
- How the new APKs were installed: the pull returned the CSVs of 14 Oct as well (10 files, 95,639 bytes), so PayProbe's data folder survived; probably it was replaced, not uninstalled (hypothesis). The student ran the install commands without checking their output and does not know whether the uninstall ran. One likely cause is a flaw in the command block given in the chat: only the uninstall was inside the backup guard, so the `adb install -r` lines ran even when the guard printed STOP (if `~/Desktop/payprobe_phone_backup` was empty or missing). Whether that backup folder exists was not checked.
- That the hotspot run matches the plan: the plan was the test phone connected to another phone's hotspot; here the test phone was the host. Whether the friend's traffic was in NetValve's tunnel (probably not, tethered traffic is not routed through the app's VPN: hypothesis) and how much the friend used during the run are unknown.
- Scheduler ON behaviour, tier persistence after an app restart, the fourth High app being refused, the Load tab, the per-flow debug log line, and all measurements.
**Decisions:** fresh uninstall chosen by the student (settings and tiers reset).
**Problems:** the Step 3 and 4 command block in the chat installed the APKs with `adb install -r` even when the uninstall guard stopped (flaw in the instruction, found 16 Oct 2026 from the old CSVs still on the phone); cosmetic problem 18 (handoff Section 8).
**Handoff state:**
- HEAD at end: 8d84169 (later commits of Session 9 and the commit that adds this entry follow it)
- Tags: pinned-base, week1-aar-built, scheduler-core, m1-payprobe-done, m2-idle-baselines-mobile
- Tree clean: after `./tools/finish_session.sh`
**Open at end:**
- Phone smoke checks still to do: `--skip 0` check of the on-screen numbers against the CSVs; proof that PayProbe traffic goes through the tunnel (NetValve Stats or Dashboard during a run); tier chips after an app restart, NetValve's fixed Low chip and the fourth High app; Load tab with NetValve ticked; scheduler ON.
- Mobile p50 on 16 Oct (242 to 243 ms) is about 35 ms above the three C1 mobile runs of 14 Oct (200, 208, 204 ms); the cause is unknown (hypothesis: signal or time of day, or the new build). A C0 and C1 pair on the same network, back to back, is the test.
- Then M2 and M6 on the hotspot (see `docs/REMAINING_WORK.md`).
**Next session, first steps:**
1. Run `./tools/session_start_check.sh` and write the Entry check.
2. Continue the phone smoke checks from the first item under Open at end.

## Session 11: 16 Oct 2026, names, Home and PayProbe screens, experiment guide
**Goal:** the student asked for a better Home (dashboard) and PayProbe screen, a clearer name than "Priority scheduler (Mode B)", and a step-by-step guide for the next phone runs (settings, entries, when to switch what on). Testing by the AI was to be kept small.
**Entry check:** not re-run in this part of the session; the newest check output is in Session 9.
**Decisions:** (a) the app says Priority Control and Shared speed; the report says priority pacer (Mode B) and rate R (handoff 9.2). (b) The Dashboard tab is renamed Home (fixes problem 18 in code). (c) PayProbe gets a run length so repeated runs are equally long. (d) The Load tab shows Mbps and the run average. (e) `summarize.py` gets a time window.
**Done (with evidence), checked by the AI in a sandbox:**
- Code written: new Home screen, `SetupChecklist` (pure), 90% and Mbps helpers in `RateUnits`, tiers on the controlled-apps chips, a colour option for `SectionCard`, PayProbe screen and run length, Load tab Mbps lines, tab and label renames, `summarize.py --from-sec --to-sec`. List of files: see the code patch `home_ui_code.patch` (archive in `tools/patches/` if you archive it).
- 74 JVM tests pass in the sandbox with a stand-in runner (63 earlier plus 11 new: 9 checklist and 90% helper, 2 Mbps helpers). Not run with Gradle.
- `summarize.py --from-sec 10 --to-sec 70` on `payprobe_C1_idle_wifi_r1_20261014_142925.csv` gave n = 60, equal to an independent count; `--skip 10` still gives n = 173 and p50 = 193 as in handoff 10.2.
- Guide written: `docs/HOW_TO_RUN_EXPERIMENT.md`.
**Claimed, not verified:**
- The Compose and View code compiles and works: it was only syntax-checked (no Android libraries in the sandbox), never built with Gradle, never seen on the phone; whether the new screens look good is unknown.
- PayProbe's run-length stop (`stopSelf` after N seconds), the "Saved as" preview and the Load tab Mbps lines on the phone.
- The expectations in the guide (Priority Control keeps PayProbe's p95 near idle) are hypotheses.
**Problems:** none new found; problem 18 (tab label wraps) is fixed in code only.
**Handoff state:**
- HEAD at end: 8d84169 (later commits of Sessions 9 and 10 and the commit that adds this entry follow it)
- Tags: pinned-base, week1-aar-built, scheduler-core, m1-payprobe-done, m2-idle-baselines-mobile
- Tree clean: after `./tools/finish_session.sh`
**Open at end:**
- Build both APKs (`./gradlew :app:assembleDebug -Pnetvalve.netstack=true :payprobe:assembleDebug`) and `./gradlew test --continue`; paste compile errors if any.
- Install (stop the VPN first) and look at the Home, Load and PayProbe screens; report what looks wrong.
- Then the phone smoke checks left in Session 10, then the experiment in `docs/HOW_TO_RUN_EXPERIMENT.md`.
- M6 to M8 not started.
**Next session, first steps:**
1. Run `./tools/session_start_check.sh --full` and write the Entry check.
2. Fix any build error, then the screens on the phone.

## Session 12: 17 Oct 2026, new screens seen on the phone, small UI fixes
**Goal:** check the Session 11 screens from the student's screenshots and fix the small things found (the student chose "fix now").
**Entry check:** `./tools/session_start_check.sh` (quick mode, AI sandbox copy of `qos-priority-scheduler-20261017-1318.zip`): 16 ok, 2 warnings (the two known 3-row mobile CSVs); the phone section was skipped (no adb in the sandbox). HEAD `939d4f0` "Session 11: ...", branch `qos-scheduler`, tree clean, tags pinned-base, week1-aar-built, scheduler-core, m1-payprobe-done, m2-idle-baselines-mobile. `docs/HOW_TO_RUN_EXPERIMENT.md` in the zip is identical to the file the student uploaded. The student's `git status -sb` showed `## qos-scheduler...origin/qos-scheduler` (nothing ahead; local tracking ref, not a live query of GitHub).
**Done (with evidence):**
- Student-reported from the pasted chat of the old account (terminal output not seen by the AI): both debug APKs built with BUILD SUCCESSFUL (30 s) and `./gradlew test --continue` ended BUILD SUCCESSFUL (1 m 12 s) for the Session 11 code.
- Screenshots `94323.pdf` (6 pages, phone clock 1:24 to 1:26): Home with Protection OFF, checklist "2 of 5 done", Priority Control card (switch OFF, shared speed 0 and 0, measured-speed fields, "Use 90% as shared speed"), Controlled apps (only PayProbe, High 1 / Medium 0 / Low 0), Tunnel settings (first three ON, auto start OFF, as in the guide); PayProbe with the how-to box, run name test, "Saved as: test", 90, the URL, READY, empty chart and the data path. No text was cut off and the bottom bar shows "Home" on one line.
- Found: problem 19 (handoff Section 8): checklist green with NetValve unticked, PayProbe fields without labels once filled, status line with zeros and no samples. The OFF switch of Priority Control looks very dim in the screenshots (not changed).
- Code written (read against the real files; checklist rule has a default parameter so the old tests still compile): `SetupChecklist.evaluate(..., ownAppTicked = true)` gives a WARN "Also tick NetValve (for the Load test)" in only-selected mode; `DashboardUiState.ownAppTicked` filled from the ticked list and the app's own package name; PayProbe label above each of the three fields; PayProbe status line shows "-" with no samples; 2 new tests in `SetupChecklistTest`.
**Claimed, not verified:**
- That the first-part Session 12 code (checklist warning, PayProbe labels, status line) passes its tests: the Mac output of 17 Oct 2026 shows BUILD SUCCESSFUL for both Gradle commands (see Update below), but the test counts were not shown and most tasks were up-to-date.
- What the changed screens look like; the Load tab, the Apps tab (tier chips, NetValve "Low (fixed)"), the PayProbe stop after 90 s, "Saved as" after a real run, and all of the experiment.
- The Mac build result of Session 11 (see above) was not seen as terminal output by the AI.
**Decisions:** small fixes now (student's choice); the dim OFF switch is left as it is.
**Problems:** problem 19 (cosmetic).
**Handoff state:**
- HEAD at end: 939d4f0 (the commit that adds this entry follows it)
- Tags: pinned-base, week1-aar-built, scheduler-core, m1-payprobe-done, m2-idle-baselines-mobile
- Tree clean: after `./tools/finish_session.sh`
**Open at end:**
- Build both APKs and run `./gradlew test --continue` again; install (stop the VPN first); in the Apps tab tick PayProbe and NetValve; look at the Apps and Load tabs and send screenshots.
- Then link-speed measurement and the runs in `docs/HOW_TO_RUN_EXPERIMENT.md` (Sections 3 and 4); the Session 10 smoke checks that are still open.
- M6 to M8 not started.
**Next session, first steps:**
1. Run `./tools/session_start_check.sh --full` and write the Entry check.
2. Fix any build error, then the Apps and Load tabs on the phone, then the speed measurement.

**Update, later on 17 Oct 2026 (second part of Session 12):**
- Mac output (student): `./gradlew :app:assembleDebug -Pnetvalve.netstack=true :payprobe:assembleDebug` BUILD SUCCESSFUL in 1 s (74 actionable tasks: 1 executed, 8 from cache, 65 up-to-date) and `./gradlew test --continue` BUILD SUCCESSFUL in 1 s (116 tasks: 1 executed, 8 from cache, 107 up-to-date). Because the tasks were mostly up-to-date, the same inputs had been built before; the test counts were not shown. Commit `4996ceb` was pushed (`939d4f0..4996ceb`). `adb install -r` of both APKs printed Success (output pasted).
- Screenshots on the phone (clock 1:51 and 1:52, 3 photos): Home, Controlled apps: "Only the 2 ticked app(s)", High 1 / Medium 0 / Low 1, chips "NetValve - Low" and "PayProbe - High"; Load tab: simulator text, download URL `speed.cloudflare.com/__do...`, upload URL `speed.cloudflare.com/__up`, auto-stop 60, buttons Download, Upload, Both, "Last run" at 0 B/s and 0 errors; PayProbe: the three labels are visible, READY, status line with dashes.
- Found: problem 20, the "Both" button label wrapped onto two lines. Code fix written (narrower padding, `maxLines = 1`); not built in the AI sandbox.
- Not seen yet: the top of Home (checklist after NetValve is ticked), the Apps tab, the Mbps lines of the Load tab (below the fold), any run. Upload URL still not verified.

**Update, 18 Oct 2026 (third part of Session 12): usage-access card**
- Student report: Home shows "Optional: usage access" although usage access was already granted. From the code (read, not run): the card used `state.device.usageAccessGranted`; `DeviceState` defaults it to false and `DeviceStateMonitorImpl` only fills it while the tunnel engine runs, so with protection OFF the card always showed. Hypothesis (not checked on the phone): some phones return MODE_DEFAULT for the app-op even when granted.
- Code written: new `service/UsageAccess.kt` (app-op, and the permission check on MODE_DEFAULT); `DeviceStateMonitorImpl.isUsageAccessGranted` uses it; `DashboardViewModel` re-reads it every 2 s while Home is subscribed (`DashboardUiState.usageAccessGranted`); `DashboardScreen` uses the new field.
- Not verified: it compiles (not built in the AI sandbox), the card disappears on the phone, and a phone that returns MODE_DEFAULT. No unit test (needs the Android framework).
- Open: build, install, check Home; the Load tab network checks from the 17 Oct night remain open.
