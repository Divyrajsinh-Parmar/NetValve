# Production-stage handoff (written 19 Oct 2026, Session 13)

**Read this first if you are a new Claude account continuing this project.** It replaces
`docs/NEXT_SESSION_PROMPT.md` (older: it plans test S4; the student has since decided to go to a
production stage). Where this file and an older document differ, THIS file wins. Everything below is
written from files, commands and student answers of Session 13; anything not verified says so.
`docs/REMAINING_WORK.md` is stale. `docs/PROJECT_HANDOFF.md` Sections 1 and 9.1 and the tracker in
`docs/IMPLEMENTATION_ROADMAP.md` Section 9 are also stale (they still say M3 to M5 were not run on a
phone); `docs/SESSION_LOG.md` is the truth, newest entries are at the end.

## 0. How to start (do this before anything else)

1. Ask the student to attach the newest project zip (with `.git`) and run `git log --oneline -10` and
   `git status --short`. Do not assume which patches of Section 3 are already committed: the AI never
   saw every commit.
2. Read, in this order: this file; `docs/PROJECT_HANDOFF.md` (Section 13 says how to talk to the
   student); `docs/SESSION_LOG.md` Session 12 (Updates 10 to 16) and Session 13 (Updates 1 to 5);
   `NOTICE.md`; `docs/IMPLEMENTATION_ROADMAP.md` (roadmap, still valid for M7 and M8).
3. Run `./tools/session_start_check.sh` on the zip. Mark anything that a document says is done but has
   no commit, file or output as "unverified".
4. Reply with: the project stage in at most 10 lines, what is verified, what is unverified, and the next
   patch. Then wait for the student.

## 1. Who the student is and the rules for you

- Student: Divyrajsinh Parmar, beginner, IITRAM, Computer Networks project. MacBook Air M1 with adb and
  Gradle. Test phone: OnePlus Nord CE5 (OxygenOS 16). Repo on the Mac: `~/Downloads/qos-priority-scheduler`,
  branch `qos-scheduler`, remote `Divyrajsinh-Parmar/NetValve`. NEVER overwrite or touch `main`. NEVER delete
  anything (no `rm` on the project; use new dated folders).
- He writes Hinglish and often DICTATES, so a word can be wrong ("attacks" was probably "apps"). If something
  is unclear, ask ONE question with the tap-options tool (`ask_user_input_v0`).
- Answer in Hinglish (Roman script), short numbered steps, beginner friendly. He wants quick answers with no mistakes.
- Claim only what a file, code or command output verifies; otherwise write "hypothesis". Never give a number from
  memory as a fact. Never say "fixed" or "works" before the student's phone result.
- You probably have no Gradle or Android SDK. Say clearly what you could not build or run. The student builds on the
  Mac and sends the errors. See Section 5 for what CAN be verified in a sandbox.
- Test commands before giving them (or say "untested"). Check where something is logged before telling him where to
  look: the app never uses `android.util.Log`; relay logs are only in the in-app Logs tab (Test tools), not logcat.
- Each code or docs change = ONE self-contained `.patch` file given with the file tool, tested with
  `git apply --check` on a clean copy that already has the earlier patches. After each patch give a SESSION_LOG entry
  (inside the patch) and ONE `./tools/finish_session.sh "message"` command. Code changes also need `PROJECT_HANDOFF.md`
  (change log) in the patch, and `NOTICE.md` if an upstream file changes.
- His zsh stops on a shell glob with no match: avoid unquoted globs in commands.
- One patch at a time; the student builds and tries it on the phone before the next one.
- Licence: base is NetValve (https://github.com/IEAmir/NetValve), Apache License 2.0. NEVER remove `LICENSE`, `NOTICE.md`
  or the upstream credits; the upstream relay engine (`network/`, `throttle/`, `netstack`) is needed. "Remove the old
  project" means: hide upstream screens and options from the user, rename in the UI, keep the credit in `NOTICE.md` and in
  an About screen. Every changed upstream file must be listed in `NOTICE.md`.

## 2. The decision (18 and 19 Oct 2026) and the product requirements

No more controlled tests. The student wants the FINAL production-stage app and will use it himself for 2 to 3 days on all
his networks; that real use is the final test. The DEBUG build (`dev.netvalve.debug`) is the one that was tested and is the one
he installs. The release build has minify and shrink on (R8, never run) and is unsigned without `keystore.properties`: do not
switch him to it without telling him the risk.

Requirements (his words, condensed): (1) the UI is white (light) always; (2) no technical terms or options the user should
not see: only protection on/off with a big Pause switch, a list where he picks PRIORITY apps, and a simple view that the
priority apps get their data well; (3) hide, do not delete (Load, Logs, per-app rule editor, manual shared speed, tier
chips) in a hidden "Test tools"; (4) automatic setup (first-run permission guide, automatic shared speed R, boot start);
(5) a small built-in health summary (running time, restarts, failures, bytes per level) as evidence for roadmap criteria 3
to 5 and for the report; (6) must work with Developer options OFF (code check: no dependency; only debug StrictMode that logs);
(7) PayProbe (second APK, `payprobe/`) simple and automatic; (8) branding and licence as above; (9) docs, tracker and a NEW final
report.

Decisions taken in Session 13 (student answers):
- **Ordered priority container.** The first app of the list has the highest priority, the next the next; the order can be
  changed; every other app is in a "normal container" below. Implemented as a ranked engine (Section 3, A1).
- **Usage access is NOT asked** (the student could not choose and asked the AI to pick the higher-performance option; code
  check: usage access only feeds `DeviceState.foregroundPackage`, used by the per-app foreground rules in Test tools; the
  priority scheduler resolves apps by UID and never reads it).
- **Banking and UPI apps: no separate exclusion screen.** The student says payment apps require the VPN to be off, so he
  switches protection OFF for them. Home says so. Pause keeps the VPN running (passthrough inside the tunnel), so only OFF
  removes the VPN (hypothesis: a payment app that checks for a VPN still sees it during Pause).
- **Developer options OFF only at the very end** (after the final APK is installed): USB debugging goes with it, so no
  `adb install` or `adb pull` afterwards. PayProbe's Share button (patch C) removes the need for `adb pull`.
- **Work order (agreed 19 Oct):** B2 result, B3, E (demo), C, D.
- The "shared upload R" stays 0 (not scheduled) unless an upload speed can be measured safely.

## 3. What has been done (Session 13) and how well it is verified

| Patch | What | Evidence |
|---|---|---|
| A1 | New pure-Kotlin ranked scheduler: `PriorityLevels`, `RankedPacerCore`, `RankedPacer`, `PriorityOrder`, `LevelResolver` (level 0 = PayProbe locked, levels 1 to 5 = the user's list, level 6 = normal; at most 5 apps) + 30 tests | Sandbox: 86 of 86 scheduler tests pass. Student: `./gradlew test --continue` BUILD SUCCESSFUL (test count not printed). Commit `cfde55c`. |
| A2 | `SchedulerController` uses the ranked pacer; `PriorityStore` (+ `PriorityOrderRepository`, DataStore key `priority_order_json`); Apps tab = ordered priority list; simple Home (on/off switch + Pause); hidden Test tools (7 taps on the small "NetValve" text on Home) holding Advanced home, Technical apps, Load, Logs; always-light theme (`Theme.kt` + manifest base theme) | Student on the phone (one phone, yes/no answers): white UI, order change, Pause, Test tools: all YES; nothing wrong seen. NOT measured: that the order really changes who gets data under load. |
| F1 | `netstack/bridge.go` `Tunnel.Stop()` removes the NIC and closes the TUN fd itself (pinned gVisor `fdbased` `Close()` is a no-op, so the VPN key icon stayed after Stop); `netstack.aar` rebuilt on the Mac | Student: key icon gone a few seconds after Stop, repeated in the B1 test. The Logs lines "netstack stopping"/"TUN fd closed" were never reported. |
| R1 | Docs: phone results of A2 and F1 | docs only |
| B1 | First-run permission guide (`ui/setup/`: notifications, VPN consent, battery exemption; read live in `onResume`; nothing remembered; only notifications and battery can be skipped, only after one try), boot auto-start on by default (`applyProductionDefaults`), Home hint about payment apps | Sandbox: 6 tests. Student: all 5 phone checks "WORKING" (guide shows only the missing step, Allow, "Open app settings"/Skip, key icon, auto-start after a restart). |
| B2 | Automatic shared speed (`calibration/`: `RatePolicy`, `SpeedMeter`, `LinkSpeedTest`, `CalibrationRepository`, `AutoRateManager`; started in `NetValveApp.onCreate`): measures about 8 s, discards 2 s, R = 0.9 x steady speed, stored per network type, re-measured on a network change and every 20 min at a quiet moment, failure keeps the last known R or 0 (no shaping); upload R = 0; logs "auto speed: ..." lines in the Logs tab | Sandbox: 15 tests (including the real socket code against a local server). Student (19 Oct): "all things are perfectly working" (no numbers). **Student screenshots (20 Oct, mobile data):** Logs show `known value 72.5 Mbps` at tunnel start (15:28:55), `measured 122.3 Mbps` 11 s later, `measured 81.5 Mbps` at 15:49:14 (about 20 min later); Advanced home shows shared download 81.5, upload 0. Still unseen: the Wi-Fi to mobile re-measure, "no hang while measuring". |
| B3 | `scheduler/LevelByteCounter` + counting in `SchedulerController.acquire`; `health/` (`HealthCounters`, `HealthRules`, `HealthRepository`, `HealthRecorder`), simple Stats tab (`StatsScreen`, `HealthViewModel`), old screen kept as `TechnicalStatsScreen` in Test tools, Share text | Sandbox: 26 new JVM tests pass (counter 5, controller counting 3, counters 8, rules 10) and the 6 old `SchedulerControllerTest` tests still pass; main pure sources compile with warnings as errors. Android and Compose code (recorder, view model, screen, navigation, DI) NOT compiled by anyone; nothing seen on a phone. |
| E1 | Live demo: `demo/` (`DemoGate`, `LatencyStats`, `DemoOrchestrator`, `HttpProbe`, `DemoRunner`), `ui/demo/`, Stats tab button; small edits in `RuleEngine`, `NetValveVpnService`, `FlowSupervisor`, `SchedulerController`, `AutoRateManager` | Sandbox: 18 new JVM tests pass (latency maths 5, gate 1, orchestrator 9, probe 3, plus the earlier ones: 50 in all with B3 and `SchedulerControllerTest`); main pure sources compile with warnings as errors. Android and Compose code NOT compiled by anyone; nothing seen on a phone. B3: the student wrote "all are right" (20 Oct); no Stats screenshots seen, so B3 is user-reported only. |
| E1b | Demo validity check and download diagnostics (`LoadGenerator.problem`, `PhaseResult.valid`, red card, report line, per-phase INFO log) | First phone run of E1 (student, 20 Oct): demo ran start to end, but the big download gave 0.0 Mbps in BEFORE and AFTER, so the comparison is invalid; cause unknown (hypotheses in SESSION_LOG Update 8). E1b sandbox: 21 demo tests pass; Android edits not compiled by anyone; nothing seen on a phone. |
| E1c | Demo download over IPv4 only (address looked up before the tunnel restart; `SimHttp.Target.connectIp`, `TrafficSimulator.start(..., downloadConnectIp)`) | Second phone run (student, 22 Oct, with E1b): red card and `demo:` lines seen; cause of 0 Mbps = IPv6 connect timeout to `speedtest.mumbai1.linode.com` (2400:8904::...) inside the tunnel while the IPv4 probe worked (72 of 72 answered). E1c sandbox: 13 simulator tests pass; Android edit not compiled; nothing seen on a phone. Open: does IPv6 of other apps time out in the tunnel on mobile data? |
| E1d | `applyAppList` skips the own package in the ticked set during the demo; `demo/ServerChooser` (Linode Mumbai, then Tele2; checked before the demo); `SimHttp.open` tries all addresses, IPv6 and IPv4 in turn; wording "priority paused, tunnel still on" | Third phone run (student, 22 Oct, with E1c): still 0 Mbps; evidence says the download sockets were outside the tunnel (local address 192.0.0.4, not 10.111.0.2) and Linode did not answer from the real network (auto speed WARN lines at 12:22, 12:24, 12:35 on IPv6; demo IPv4 timeout). The E1c cause (IPv6 inside the tunnel) was wrong. E1d sandbox: 43 tests pass; Android edits not compiled; nothing seen on a phone. |
| E1 result | First VALID live demo on the phone (student's Share text, 22 Oct, one run, network not stated) | Quiet line p95 145 ms, jitter 22 ms. BEFORE (priority paused): p95 440 ms, jitter 106 ms, download 97.2 Mbps, BAD. AFTER (priority on): p95 146 ms, jitter 26 ms, download 15.7 Mbps, GOOD. Median unchanged (125 versus 108 ms is noise). One sample: repeats on mobile data and Wi-Fi needed. |

The commit state of A2, B1 and B2 on the Mac is unknown to the AI (known: `cfde55c` = A1, `b3e6946` = F1 + R1 + earlier). Check
`git log --oneline -10`.

## 4. Where things are (map for the production features)

- Settings: `data/model/GlobalSettings.kt` (production values: `selectionMode = ALL_EXCEPT`, `schedMode = PRIORITY_PACER`,
  `autoStartOnBoot = true`, `schedRateDownBytesPerSec` = R set automatically, `schedRateUpBytesPerSec = 0`).
- Relay and scheduler: `network/` (upstream relay), `scheduler/` (`SchedulerController` is the one object the relay talks to;
  `RankedPacerCore` strict levels with floors 0.10 (middle) / 0.05 (normal) and caps 0.50 (middle) / 0.20 (normal) while any more important
  level is active, hold 2 s; the old `PriorityPacerCore`/`SchedulerPacer`/`Tier*` classes stay but are unused on the device).
- Tunnel: `service/NetValveVpnService.kt` (in `ALL_EXCEPT` the app's own package is always excluded: `addDisallowedApplication`),
  `service/VpnController.kt` (status flow), `service/TrafficEngine.kt` (`statsFlow`), `service/BootReceiver.kt`.
- UI: `MainActivity.kt` (gate: `ui/setup/SetupGuideScreen` until nothing blocks), `ui/navigation/Navigation.kt` (tabs Home, Apps, Stats;
  routes `tools`, `tools/home`, `tools/apps`, `sim`, `logs`), `ui/home/HomeScreen.kt`, `ui/priority/PriorityScreen.kt`,
  `ui/tools/ToolsScreen.kt`, `ui/stats/StatsScreen.kt` (still the old technical screen).
- Logging: `log/Logging.kt` (`Logger`, `LogCategory`); the Logs tab shows them. Lines of the Go bridge arrive through the same handler.
- PayProbe: `payprobe/` (second APK, `dev.netvalve.payprobe`, locked to level 0 in `PriorityStore`).
- Tools: `tools/finish_session.sh "message"` (checks, commits, pushes, prints the SESSION_LOG lines that name the entry check),
  `tools/session_start_check.sh`.

## 5. Environment and how to build, test and install (verified by the student's outputs unless marked)

- Test on the Mac: `./gradlew test --continue`. Build the REAL engine: `./gradlew :app:assembleDebug -Pnetvalve.netstack=true`.
  **A plain `./gradlew assembleDebug` builds the LOOPBACK stub engine, which does not forward traffic: never install it** (an AI mistake
  in Session 13 caused by copying the plain command). Install: stop the VPN on the phone first (OxygenOS 16 can drop the internet when a
  VPN-running app is updated), then `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
- Rebuild the Go library (only when `netstack/*.go` changes): `cd netstack && ./build-aar.sh`. On the Mac: Go `go1.27.1 darwin/arm64`
  (`/opt/homebrew/bin/go`), `ANDROID_NDK_HOME=/Users/divyrajsinh/Library/Android/sdk/ndk/27.1.12297006` (a second NDK 27.2.12479018 is
  installed). `build-aar.sh` overwrites `app/libs/netstack.aar`: back the old one up first (done once: `~/Downloads/netstack_old_20261018.aar`).
  It also runs `go mod tidy`, which changed `netstack/go.mod` and `go.sum` (only indirect versions of `golang.org/x/mobile` and `golang.org/x/sys`).
- Patch handling pitfall seen three times: `git apply --check ... && git apply ...` prints NOTHING when it succeeds; if the student runs the
  same command twice, the second run fails with "already exists"/"does not apply". That means "already applied", not "broken". Check with
  `git status --short` and by grepping for a unique string of the patch. Also the downloaded file may carry a different name (spaces or "(1)"): ask for
  `ls -l ~/Downloads | grep -i patch`.
- Sandbox verification that WORKED in Session 13 (you can repeat it): download the Kotlin compiler from
  `https://github.com/JetBrains/kotlin/releases/download/v2.0.21/kotlin-compiler-2.0.21.zip` (GitHub is reachable from the sandbox, Maven and
  go.dev are not); compile the pure packages (`scheduler/`, `calibration/` except `AutoRateManager`/`CalibrationRepository`, `ui/setup/SetupGuide.kt`,
  `sim/SimHttp.kt`, `data/model/Enums.kt` with a stub `kotlinx.serialization.Serializable`) with `-Werror` and run the tests with a small stand-in JUnit
  (`org.junit.Test`, `Assert`) and reflection runner; the coroutines jar is inside the Kotlin distribution (`kotlinc/lib/kotlinx-coroutines-core-jvm.jar`).
  Android and Compose code (screens, view models, `MainActivity`, `AutoRateManager`'s real Android calls, DataStore, Hilt wiring) CANNOT be compiled there:
  only the student's Gradle build checks them. A single sandbox command is cut after 300 s: run long test suites in separate commands.
  No Go toolchain in the sandbox: Go changes are unverified until the student rebuilds the AAR.
- The gVisor source (for reading, not building): `https://codeload.github.com/SagerNet/gvisor/tar.gz/refs/tags/v0.0.0-20250811.0-sing-box-mod.1`.

## 6. Remaining work (the agreed order). Specs are DESIGNS, no code exists yet.

### B3: health summary and simple Stats (written 20 Oct 2026; see Section 3; waits for the student's build and phone check)
- Persisted counters over days (DataStore, own key): running time, number of tunnel starts and restarts, failures (establish failed, engine errors,
  auto-speed failures), bytes per level (priority apps vs normal; needs a byte counter in `SchedulerController`/relay), and the last measured R per network type.
- Stats tab rewritten simply (no technical words): "your priority apps are getting their data well" with an easy status (good / check) and a few big
  numbers; the old technical stats screen goes into Test tools (hide, do not delete).
- The summary must give evidence for roadmap criteria 3 to 5 (UID share at least 95 percent, 10-minute run, 1-hour run) and for the report; label how each number is computed.
- Safety: a failure of the counters must never affect traffic.

### E: "does it really work" demo and real-life record (requested 19 Oct)
Student answers: both a one-tap Live demo and an automatic real-life record; show ping, a simple Lag score (good / okay / bad), packet loss and jitter, and the
priority app's download speed; numbers + graph + a "lag feel" animation (a ball or bar that stutters with the real ping). Honest limits to state in the app: FPS cannot be
measured (Android gives no other app's FPS without root or ADB tools; hypothesis); packet loss and jitter are PROBE-BASED proxies (a probe that gets no answer = lost; jitter =
variation of successive latencies), not real packet loss.
- Live demo (about 30 s): a big background download plus continuous small latency probes; 15 s with Pause ("before") and 15 s with protection ON ("after"); side by side.
- Technical catch (code facts): in the production settings the app's own package is EXCLUDED from the tunnel, so its own load would not be shaped. The demo therefore needs a
  demo mode that includes the app in the tunnel (one tunnel restart). All traffic of the app has the same UID, so load and probe would be the same level; plan: a
  destination-based rule in demo mode (the speed-test host = normal level, the probe = priority level), which needs the destination passed to the level lookup in the relay
  (`SchedulerController.acquire(uid, flowId, lane, bytes)` does not receive it; whether `FlowSupervisor` has the destination at that point was NOT checked). Alternative: PayProbe as the probe (separate APK, already level 0).
- Real metric for real apps: measure the QUEUE DELAY per level inside the pacer (time between `acquire` and the grant) and show "priority apps waited X ms, other apps Y ms";
  it needs no probe and works for the student's real game.
- Real-life record: probes every few seconds during use, stored with the state (ON/Pause) and a busy flag, aggregated; shown in Stats ("busy-time lag with NetValve").
- Lag-score thresholds are not decided (hypothesis needed; choose and label them).

### C: PayProbe simple and automatic
Preset address `https://connectivitycheck.gstatic.com/generate_204`, automatic run name and length, live latency number and graph, results readable in the app, a Share button for the CSV
(FileProvider; no `adb pull`). Keep the old behaviour reachable (do not delete).

### D: docs, tracker, NOTICE, About screen, final report
- Update `SESSION_LOG.md`, `PROJECT_HANDOFF.md` (change log; fix the stale Sections 1 and 9.1), `NOTICE.md`, the progress tracker in `IMPLEMENTATION_ROADMAP.md` Section 9 (M3 to M5 say "nothing run on the phone": stale; M7.1 is now done by B2),
  `docs/REMAINING_WORK.md` (stale).
- `NOTICE.md` must name EVERY changed upstream file: `git diff --name-status pinned-base HEAD` lists more modified upstream files than NOTICE names today
  (for example `GlobalSettings.kt`, `RuleEngine.kt`, `DeviceStateMonitorImpl.kt`, `TrafficEngine.kt`, `FlowSupervisor.kt`, `Components.kt`).
- About screen with the upstream credit (NetValve by IEAmir, Apache 2.0) and the licence.
- A NEW final report: ask the student ONCE for the format (Word, PDF or Markdown). Content: the tests done (S1 and its numbers: loaded p95 of C4 = 3.7 to 21 percent of C1 in 5 of 5 pairs; the three-network
  observations: college Wi-Fi had no queue, mobile data had a queue in 1 of 3 runs (C1 only)); the production stage; the final verification = 2 to 3 days of real use. Honest limits: one phone; one hotspot for the controlled
  test; R was fixed by hand in that test; roadmap criteria 2 to 5 not tested in the controlled setting; S2/S3 (upload) not tested; the Low app got about 20 percent of R while a High app was active (hypothesis from the numbers).

## 7. Open items and risks to keep in mind

- Never verified on a phone: the NAT64 Gradle output (9 `Nat64Test` tests) and the Logs line "IPv4 unreachable, using NAT64"; `TrafficSimulator.uploadLoop` never reads the server answer; `HOW_TO_RUN_EXPERIMENT.md`
  protocol is outdated; Session 11 and 12 patches are not archived in `tools/patches`; the GitHub "old project removal" question is undecided (never touch `main`); `finish_session.sh` prints 2 WARN for old tiny CSVs in
  `docs/data/mobile_2026-10-14` (`payprobe_C1_idle_mobile_r0_20261014_122143.csv`, `..._r1_20261014_122201.csv`): put them in the next Entry check every time.
- Unexplained (20 Oct, Logs screenshots): repeated W/ERROR `connect failed TCP:10.111.0.2:...>192.168.0.1:8013 ... after 10000ms` on mobile data. Port 8013 and 192.168.0.1 are not in this project; hypothesis: some other app asks for a private router-like address that cannot be reached; the app is not known (the Logs screen hides the UID; Share/export has a uid column). B3 counts these apart as "not a scheduler fault".
- Documents disagree on the roadmap success criteria 3 to 5: `IMPLEMENTATION_ROADMAP.md` says (3) no starvation, (4) 95 percent of bytes to a real UID, (5) 1-hour run without a crash; Section 6 B3 above says UID share, 10-minute run, 1-hour run. B3 prints the evidence for all of them (longest wait, attributed percent, longest run); the wording is for patch D.
- B2 risks: the speed-test file `http://speedtest.mumbai1.linode.com/100MB-mumbai.bin` can become slow or unavailable (then R stays at the last known value or 0); a measurement during heavy use can read low; a single TCP
  connection may under-read a very fast link; the default-network callback of an excluded app reporting the REAL network is a hypothesis about Android.
- Middle ranks (cap 0.50) of the ranked pacer have never run under load on a phone; only the 0.20 cap of the lowest level was measured in S1 (as "Low").
- Boot auto-start only restarts the tunnel if protection was ON before the restart and the VPN consent still stands; vendor auto-launch settings of OxygenOS may block it in other situations.
- The release build (R8 minify, unsigned) has never been run.
- Developer options OFF only at the end (Section 2).

## 8. Prompt to paste into a new Claude chat

> Project: priority scheduler on NetValve (repo ~/Downloads/qos-priority-scheduler, branch qos-scheduler, remote Divyrajsinh-Parmar/NetValve; never delete anything and never overwrite main). I attached the latest project zip (it includes .git).
> Read docs/PRODUCTION_STAGE_HANDOFF.md first and follow it (Section 0 says how to start, Section 1 the rules). I am a beginner student, I write Hinglish and dictate, so ask me ONE question with the tap-options tool when something is unclear.
> Continue with the next item of Section 6 (check `git log --oneline -10` first). Give me one patch at a time, tested with git apply --check, with a SESSION_LOG entry inside and one finish_session.sh command.
