# Implementation Roadmap: finishing the Priority Scheduler

**Version 2.0 · 12 Oct 2026** · Build plan for Claude (or any AI) to execute one milestone at a time.
Companion to `docs/PROJECT_HANDOFF.md`, which says where the project IS. This file says what is LEFT and in what order.

**This is not a week-by-week plan.** The work is ordered by *dependency*: do the next unfinished milestone whenever you have time. Each milestone has a clear "done" test, so progress is measured by evidence, not by dates.

## 0. What already exists (so we do not rebuild it)

Done and verified on a JVM (36 tests, plus a bottleneck simulation, see handoff Sections 9 and 10): `Tier`, `SchedulerConfig`, **`PriorityPacerCore`** (Mode B), **`AdaptiveThrottle`** (Mode A rule), `CalibratorMath`, **`SchedulerPacer`** (coroutine adapter with `acquire(tier, flowId, lane, bytes)`), `TierAssignments` (tier rules). The simulation already supports the algorithm claims. **What is missing is everything that touches the phone:** wiring, UI, the measuring app, and measurements.

## 1. The best way to finish (strategy)

1. **Measure first, build second.** PayProbe (M1) and the Go/No-Go experiment (M2) need no scheduler code and tell us whether the idea pays off on this phone and network. If they say no, switch to the simulation-centred fallback early instead of after weeks of wiring.
2. **One small wiring change, not a rewrite.** The scheduler plugs in at four call sites in `FlowSupervisor` (M3). Mode OFF must behave exactly like today.
3. **Fixed rate R before calibration.** Pick R by hand (0.9 x measured goodput) first. On-device calibration is a stretch item (M7).
4. **Mode B is the contribution.** Mode A stays a simulation result unless time remains (M7).
5. **No separate Bypass screen.** In ONLY_SELECTED mode an unticked app is already outside the tunnel (Bypass). Tier chips apply only to ticked apps.
6. **Always keep the fallback alive.** If the phone effect is weak, the simulation plus tests plus honest on-device numbers is still a complete, defensible project (Plan C).

**Fast path:** M0, M1, M2, M3, M4, M5, M6, M8. **Stretch:** M7.

```
M0 adopt -> M1 PayProbe -> M2 Go/No-Go --(GO)--> M3 wire scheduler -> M4 tier UI -> M5 traffic simulator -> M6 evaluation -> M8 report
                                  \--(weak effect)--> try hotspot/uploads/fixed R; else fallback (Section 8)
M7 stretch items can be done any time after M3.
```

## 2. Ground rules for the AI

- Never rewrite the relay or the netstack. Our logic lives in `dev.netvalve.scheduler` (pure Kotlin, JVM tests). Upstream edits are tiny and listed in `NOTICE.md`.
- Read the real code before proposing a change; quote file and function names. Mark anything unverified as a hypothesis.
- Do not use `removeFirst()` or `removeLast()` on `MutableList` or `java.util` collections (JDK 21 binds them to Java methods that crash on older Android). `kotlin.collections.ArrayDeque` is fine.
- Beginner student: small numbered copy-paste steps, Hinglish story-style explanations, ask for the output after every step. Ask for the full Gradle "What went wrong" text instead of guessing.
- Honest claims only: local and server-free; never secure or private. Report medians and spread, never only the best run.

## 3. Done-protocol (after EVERY milestone)

1. `./gradlew test` passes; `./gradlew :app:assembleDebug -Pnetvalve.netstack=true` succeeds; the on-phone check named in the milestone passes (or the failure is recorded honestly).
2. Commit as `M<n>: short description`; tag milestones (`m3-wired`, and so on).
3. If an upstream file changed, add it to `NOTICE.md`.
4. **Update `docs/PROJECT_HANDOFF.md`:** status table (Section 1), results (Section 10), problems (Section 8), change log (Section 15).
5. Tick the tracker (Section 9 below).
6. Re-create the zip with the terminal command from handoff Section 7 (never Finder).
7. Add the session entry to `docs/SESSION_LOG.md` (every "done" with evidence) and commit it together with the work. At the start of the next session run `./tools/session_start_check.sh` first.

## 4. Verified code facts you will need

| Fact | Where |
|---|---|
| TCP pacing calls: `throttleManager.pace(upBucket, n.toLong())` (about line 95) and `pace(downBucket, ...)` (about 109) | `network/FlowSupervisor.kt`, `relayTcp` (starts line 55); buckets from `bucketFor(uid, Direction.UPLOAD/DOWNLOAD)` (lines 83-84) |
| UDP pacing calls at about lines 180 and 190; DNS exemption `dnsExempt = config.exemptDns && ctx.isDns` (line 136); buckets are null for DNS | `relayUdp` (starts line 129) |
| Unattributed UID is `UidResolver.UID_UNKNOWN = -2` (invalid is -1) | `network/UidResolver.kt` |
| `ThrottleManager` is built in `EngineModule.provideThrottleManager(ruleEngine, @AppScope scope, logger)`; an app-wide scope exists as `@AppScope CoroutineScope` | `di/EngineModule.kt`, `di/DataModule.kt` |
| `GlobalSettings(selectionMode = ONLY_SELECTED, defaultDownload, defaultUpload, ipv6Mode, exemptDns, logLevel, autoStartOnBoot, enabled)` stored as JSON in DataStore | `data/model/GlobalSettings.kt` |
| TUN routing: ONLY_SELECTED allows only ticked apps (our own package is **inside** the tunnel only if ticked); ALL_EXCEPT always excludes our own package. The list is fixed at `establish()`, so changes need a tunnel restart | `service/NetValveVpnService.kt`, `applyAppList` |
| Relay sockets are `protect()`-ed, so our own flows do not loop | `ConnectionManager.kt` |

## 5. Milestones

### M0. Adopt this package and verify (about 30 minutes)

Steps: handoff Section 7 ("Adopt this package"). Run `./gradlew test` (upstream 7 suites plus our 5 new ones) and build and install the real-engine APK.
Done when: tests green, APK installs, VPN starts, a browser loads a page. If a test fails, paste the first failure block; likely suspects are coroutine test timing or a missing import, not the algorithm (it passed 36 tests on a JVM).

### M1. PayProbe v0: the measuring instrument

Why first: every later result (Go/No-Go, evaluation) is a PayProbe CSV.
Build: new Gradle module `:payprobe` (add to `settings.gradle.kts`), `applicationId dev.netvalve.payprobe`, signed with the same debug keystore (building both on the same Mac does this).
- Foreground service (specialUse type, like the main app) so it keeps running with the screen off.
- Every 1 s (configurable) a **fresh** HTTPS GET with a new connection each time (HTTP/1.1, no connection reuse, `Connection: close`) to a tiny endpoint, for example `https://connectivitycheck.gstatic.com/generate_204` (alternatives: `https://cp.cloudflare.com/generate_204`; verify they answer on the test network and record the date).
- Record total transaction time (connect + TLS + request + response). A 5 s timeout counts as a failure and is stored as 5000 ms.
- Screen: Start and Stop, live last value, p50, p95, max and timeout count so far, a Compose Canvas line chart of the last 120 samples (about 60 lines, no chart library), a run label field (for example `C1_S1_run3`), and CSV export.
- CSV columns: `iso_time, elapsed_ms, latency_ms, ok, http_code, label`. Save under the app-specific external folder so `adb pull` works.
- No broadcast to the main app yet (M7).
Done when: a 5-minute run gives a CSV that `adb pull` retrieves, the graph moves, and p50/p95 match a spreadsheet check of the CSV.
Note: PayProbe must be **ticked in NetValve's Apps list** (ONLY_SELECTED mode) to go through the tunnel. A run with VPN off is configuration C0.

**Status (14 Oct 2026): DONE on the phone.** Builds with Gradle; the first launch crashed (insets controller before `setContentView`) and was fixed; after that, runs of 3 to 4 minutes wrote CSVs that `adb pull` retrieves and `analysis/summarize.py` reads. The CSV has one extra column, `note` (exception name or `timeout`, empty on success). Files: `payprobe/build.gradle.kts`, `payprobe/src/main/AndroidManifest.xml`, and under `payprobe/src/main/kotlin/dev/netvalve/payprobe/`: `Prober.kt`, `ProbeService.kt`, `ProbeState.kt`, `ProbeStats.kt`, `CsvFormat.kt` (also holds `CsvSink`), `ChartView.kt`, `MainActivity.kt`, `ProbeModels.kt`; tests in `payprobe/src/test/`. Build and install: `./gradlew :payprobe:assembleDebug` then `adb install -r payprobe/build/outputs/apk/debug/payprobe-debug.apk`. Fetch results: `adb pull /sdcard/Android/data/dev.netvalve.payprobe/files/ docs/data/runs/` then `python3 analysis/summarize.py docs/data/runs/`. PayProbe is locked to the High tier in `TierAssignments` by its package name. Default probe URL `https://connectivitycheck.gstatic.com/generate_204`. The system DNS cache still applies (as for any app), so after the first probe the DNS part is small.

### M2. Go/No-Go experiment (no new code)

Goal: decide whether the idea pays off on this phone.
Setup: own router or phone hotspot, not college WiFi. Same place and time of day. Note phone, OxygenOS build, network type and signal.
1. Measure link speed (a speed test while nothing else runs) and set R = 0.9 x measured downlink.
2. **Load:** 3 to 4 parallel large downloads from one app, for example Chrome tabs or a download manager, or Termux (from F-Droid) with parallel `curl -o /dev/null <large file URL>` jobs. Use a large test file you verify is reachable (for example Cloudflare's `https://speed.cloudflare.com/__down?bytes=100000000` or a Hetzner speed-test file).
3. **Runs of 60 s each, at least 5 repeats:** (a) VPN on, no control, load on, PayProbe running; (b) the same with NetValve's existing static cap on the downloader at about 20 percent of the link (per-app limit in the app's detail screen).
Decision on PayProbe p95: about 2x lower or better in (b) than (a) means **GO**. Barely changes: try uploads, the hotspot, or a lower cap; if an effect appears, GO with fixed R as the primary demo and report calibrated results honestly. Forwarding unstable or builds failing after a focused effort: fallback (Section 8).
**Progress (14 Oct 2026).** Three VPN-on idle runs on mobile data are recorded (handoff Section 10.1): p50 200 to 208 ms and p95 246 to 350 ms, so effects smaller than about 100 ms at p95 cannot be claimed. No VPN-off (C0) run exists yet, so VPN overhead is not measured. Next: repeat C1, C0, C1 idle on the network the load runs will use (own WiFi or hotspot), then load with no control (C1), then load with the static cap on the downloader (C2). Use at least 5 runs per condition and medians. Procedure: start the VPN, wait about 10 seconds, then Start PayProbe; put C0 or C1 and the network in the label.

Done when: both CSV sets are in `docs/data/`, the table (median, p95, max, timeouts) is in the handoff, and the decision is written down.

### M3. Wire the scheduler into the relay (the core engineering step)

Design: a `Pacer` call replaces the four `throttleManager.pace(...)` sites; static caps still apply on top.
1. **Settings.** Add to `GlobalSettings` (with defaults so old stored JSON still loads): `schedMode: SchedMode = OFF`, `schedRateUpBytesPerSec: Long = 0`, `schedRateDownBytesPerSec: Long = 0` (0 means unset, behave as OFF). Expose via `SettingsRepository`.
2. **Tier store.** Persist `TierAssignments.snapshot()` as a package-to-tier map in DataStore (new `TierRepository`); avoid a Room migration. Locked entries: our own package Low, `dev.netvalve.payprobe` High.
3. **TierResolver (Android adapter).** `uid -> PackageManager.getPackagesForUid() -> TierAssignments.tierOfPackages`, cached in a `ConcurrentHashMap`, cleared when a tier changes or a package is added or removed. `UID_UNKNOWN` and invalid UIDs map to Medium.
4. **Hilt.** In `EngineModule` provide a singleton `SchedulerPacer(@AppScope scope, upConfig, downConfig, nowMs = SystemClock::elapsedRealtime)`. Collect the settings flow in the app scope and call `updateConfig(Lane.UPLOAD/DOWNLOAD, ...)` when mode or rates change. Give `FlowSupervisor` the pacer and the resolver through its constructor.
5. **Call sites.** At each of the four sites: if mode is `PRIORITY_PACER`, rate is set, and the flow is not DNS-exempt, first `pacer.acquire(tier, flowId, lane, bytes)` then the existing `throttleManager.pace(bucket, bytes)`. Allocate `flowId` from an `AtomicLong` per flow and per relay, and call `pacer.releaseFlow(flowId)` in a `finally` when the relay ends. Mode OFF, unset rate, or DNS-exempt flows skip the pacer entirely.
6. **Minimal controls.** In the Dashboard: a mode switch (OFF or Mode B) and two fields for R up and R down in Mbps (converted to bytes per second). Log a debug line per flow start: tier, mode, R.
7. **UDP drop policy.** The roadmap assumed tail-drop for Low; the code uses DROP_OLDEST in `PacingQueue`. Keep it, record the decision in the handoff.
Done when (on the phone): with the three downloads from M2 running, PayProbe (High) p95 in Mode B with fixed R is clearly lower than in Mode OFF. Mode OFF behaves exactly as before. Unit tests still green; add a small test for the TierResolver cache logic with a fake package source.
Risks: downlink control is indirect (we slow reads and TCP reacts later); try smaller upstream socket buffers (about 64 KB, in `ConnectionManager` before connect) and report up and down separately.

### M4. Tier UI

In the Apps screen, for each ticked app show a tier chip (Medium default; tap to cycle Medium, High, Low) using `TierAssignments.set`. On `HIGH_FULL` show an explanation that High holds at most 3 apps. Our own app and PayProbe show locked chips. Unticked apps show a "Bypass: not controlled" label. A tier change must take effect on in-flight flows (the resolver cache is cleared and the next `acquire` uses the new tier); a tick change keeps the existing "apply" restart banner.
Done when: tier changes persist across restarts and take effect without restarting the transferring app; the fourth High app is refused with the explanation.

### M5. In-app traffic simulator (the Low app)

A screen in the main app with Down, Up and Both buttons: 3 parallel downloads and 2 parallel uploads of generated random bytes, HTTP/1.1, separate connections, configurable endpoints, live throughput. It runs under our own UID, which is the Low tier. **To put it inside the tunnel, tick NetValve itself in the Apps list** (ONLY_SELECTED mode; ALL_EXCEPT always excludes our own package). Its relay sockets are protected, so there is no loop.
Done when: the simulator's traffic appears as our own app in per-app throughput and is paced as Low.

### M6. Evaluation on the phone

Configurations on the device: C0 VPN off; C1 VPN on, Mode OFF; C2 static cap on the simulator app (existing feature, about R/5); C4 Mode B. (C3 and C5 stay simulation results unless M7 adds them.)
Scenarios, each 60 s, at least 5 repeats, same place and time: **S1** downlink contention (3 downloads Low + PayProbe High), **S2** uplink (2 uploads), **S3** both, **S4** High idle (only Low: Low goodput versus C2), **S5** 5 to 10 minute run (no scheduler-caused failures), **S6** a real app as the heavy flow, **S8** PayProbe left unticked (Bypass) while Low is shaped (the banking-app case; report whatever is measured). S7 (WiFi to mobile switch) only if time permits.
Metrics: PayProbe median, p95, p99, max, timeout rate; Low goodput and total goodput; overhead C0 versus C1; the share of bytes attributed to a real UID (from the stats screen).
Analysis: a small script `analysis/summarize.py` (pure Python or pandas) reads the CSV folder and prints the table and saves graphs; compare phone results with the simulation predictions in handoff Section 10.
Success criteria (fixed in advance; report honestly if missed): (1) S1 to S3: C4 p95 at most 50 percent of C1 p95 in at least 4 of 5 runs; (2) S4: C4 Low goodput at least 85 percent of R and clearly above C2; (3) no scheduler-caused starvation in S5; (4) at least 95 percent of bytes attributed to a real UID; (5) a 1-hour run without a crash.
Done when: raw CSVs, the summary table, graphs and an honest paragraph per success criterion are in the repo.

### M7. Stretch (only after M6, in this order)

1. On-device calibration using `CalibratorMath` (parallel downloads for about 8 s, discard 2 s, R = 0.9 x steady goodput, stored per transport, recalibrate when the network type changes).
2. Mode A on the device with `AdaptiveThrottle` driving the existing per-app buckets (adds C3 to the phone results).
3. PayProbe to main app broadcast (signature permission) and a live latency graph in the Dashboard.
4. Experiment runner automation and CSV export from the app.
5. Edge cases (screen off 30 min, airplane mode, another VPN revokes ours, reinstall while the VPN is active, tethering as a negative test) and the 1-hour stability run.
6. Quick Settings tile "pause control".

### M8. Report, viva and demo

- **Final report:** problem, theory, design, simulation results, phone results, honest limits, what is reused from NetValve versus built (see `NOTICE.md`).
- **Viva question bank** (prepare short answers): static cap versus priority (use the C2 versus C4 numbers); where the scheduling discipline is (inside the relay, per tick, per direction); why shaping below capacity helps (queue moves into our pacer); what happens when other people cause the congestion; why no AQM; why two APKs (priority is per UID); why a hold time and its cost (86 percent of R in idle windows); why floors (C4 versus C5); banking apps (Bypass, indirect effect, S8); why it cannot run next to another VPN; what the limits of the results are.
- **Demo (about 6 minutes):** state boundaries first; show tiers; PayProbe graph with Mode OFF then Mode B under the simulator load; stop PayProbe traffic and show Low rising toward R (work-conserving) against the static-cap result; show the evaluation table; keep a recorded backup video and the CSVs.

## 6. Experiment reference (kept short)

Mode and parameters: handoff Section 9 (tick 10 ms, grant 4096 B, hold 2000 ms, alphaMedium 0.50, alphaLow 0.20, floorMedium 0.10, floorLow 0.05). Expected extra delay for High is about tick + grant/R plus kernel-buffer residue; measure it. Optional, only if time allows: DSCP/IP_TOS on High-tier upstream sockets; do not claim an effect without measurement.

## 7. Risks to watch while building

| Risk | Response |
|---|---|
| Effect too small on this network | uploads, hotspot, fixed R; report honestly |
| Downlink shaping lags | smaller socket buffers, tune grants, report up and down separately |
| Our app or PayProbe not inside the tunnel | tick them in the Apps list (ONLY_SELECTED) and restart the tunnel |
| Another VPN revokes ours; OEM kills the service | document; battery exemption; re-enable |
| OxygenOS 16: updating the app while the VPN is on can break internet | stop the VPN before every `adb install -r` |
| UDP or QUIC not attributable | falls back to Medium; report the share; optionally disable QUIC for the demo and say so |
| Fixed-R demo looks rigged | also show calibrated mode (M7), S6 and a paused-app bound |
| A milestone drags on | use the fallback below |

## 8. Gates and fallbacks

- **After M2:** GO, GO with fixed R, or fallback.
- **After M3:** if Mode B does not beat C1 on the phone in S1 after honest tuning, record the numbers and fall back.
- **Fallback (Plan C, already largely met):** the pure-Kotlin scheduler, the 36 tests, the simulation (C1 to C5 with tables), the tier rules, PayProbe measurements of NetValve's existing static cap, and an honest analysis of why the on-device effect was small. Fully submittable.
- **Cut list, in order:** M7 items from the bottom up; S7; S6; the Dashboard graph; C2 automation.

## 9. Progress tracker (update after every milestone)

| Milestone | Name | Status | Date | Notes |
|---|---|---|---|---|
| M0 | Adopt package and verify | done | 12 Oct 2026 | 36 new tests pass under Gradle; commit 3df8724, tag scheduler-core |
| M1 | PayProbe v0 | done | 14 Oct 2026 | 9 JVM tests; runs on the phone; CSV pull and summary work; commit 4e1b6da |
| M2 | Go/No-Go experiment | in progress | 14 Oct 2026 | three VPN-on idle runs recorded on mobile data (noise floor about 100 ms at p95); VPN-off C0 missing, being redone; load runs pending |
| M3 | Wire scheduler into relay | not started | | |
| M4 | Tier UI | not started | | |
| M5 | In-app traffic simulator | not started | | |
| M6 | Evaluation on the phone | not started | | |
| M7 | Stretch items | not started | | |
| M8 | Report, viva, demo | not started | | |

## 10. Prompt templates

**Start of a session:**

> Project: priority scheduler on NetValve (repo ~/Downloads/qos-priority-scheduler). Read docs/PROJECT_HANDOFF.md and docs/IMPLEMENTATION_ROADMAP.md. We are working on milestone M<number>. First read the relevant real code and state the exact files and functions you will touch. Keep new logic in framework-free Kotlin with JVM tests. Give me small copy-paste steps, explain in Hinglish like a short story, and ask for my output after each step.

**End of a session:**

> Milestone M<number> is finished. Give me the commit command, then the exact text to add to the change log, status table and results of docs/PROJECT_HANDOFF.md, and the tracker row to update in docs/IMPLEMENTATION_ROADMAP.md.
