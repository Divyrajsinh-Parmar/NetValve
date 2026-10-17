# Priority-Aware Traffic Scheduler for Android: Project Handoff Report

**Version 2.1 · 14 Oct 2026** · Author: Divyrajsinh Parmar (Computer Engineering, IITRAM Ahmedabad) · Course: Computer Networks (college project)
Companion file: `docs/IMPLEMENTATION_ROADMAP.md` (what is left and how to build it). This file says where the project IS.

## 0. How to use this document

Give this file and `docs/IMPLEMENTATION_ROADMAP.md` (or simply the repo zip, which contains both) to a new AI chat with this message:

> Read docs/PROJECT_HANDOFF.md and docs/IMPLEMENTATION_ROADMAP.md fully. They are my project history, current state and plan. Continue with the first unfinished milestone in the roadmap tracker. Follow Section 13 of the handoff on how to talk to me.

**After every finished task** the AI must update (a) the change log (Section 15) and any section whose facts changed, (b) the tracker in the roadmap, and re-create the zip. These two files are the single source of truth; copies elsewhere are stale. A third file, `docs/SESSION_LOG.md`, records what each session did and what proves it. **At the start of every session run `./tools/session_start_check.sh`** and compare it with the last log entry; at the end add a new entry.

## 1. Executive summary and honest status

**Idea.** When several apps share one phone link (a cloud backup while a video call, an exam or a game), stock Android gives the user no way to say who wins. We add a **priority scheduler** to the relay of an open-source local-VPN traffic shaper (NetValve, Apache-2.0). High-tier apps get their bytes out first; Low-tier apps still use the whole link when nothing important is active (work-conserving). No root, no server.

| Layer | State |
|---|---|
| Development environment (Mac M1, JDK 17, Go, NDK 27, SDK 35) | **done** |
| Fork pinned at `f7b34b0`; repository defects repaired (Section 8) | **done** |
| Real network engine (netstack AAR) builds, APK installs and runs on the OnePlus Nord CE5 | **done** |
| First fix round (usage access, protect(), Dashboard tab, app icons), reported working by the student | **done** |
| Scheduler algorithm (Mode B core, Mode A rule, calibration maths, tier rules, coroutine adapter) with 36 unit tests | **done, verified on a JVM** |
| Algorithm-level simulation of all modes (results in Section 10) | **done** |
| Scheduler wired into the relay (`FlowSupervisor`) and switchable in the app | **code written 14 Oct 2026; the debug build passes on the Mac (15 Oct); not run on the phone** (M3, Section 9.1) |
| PayProbe v0: separate module `payprobe/` (probe loop, CSV, live numbers, chart) plus `analysis/summarize.py` | **done (M1, 14 Oct 2026): 9 JVM tests pass, runs on the phone, CSVs come off with `adb pull` and are summarised by the script** |
| In-app traffic simulator, tier UI | **code written 14 Oct 2026; the debug build passes on the Mac (15 Oct); not run on the phone** (M4, M5, Section 9.1) |
| Idle baselines on mobile data (three VPN-on runs) | **done, 14 Oct 2026** (Section 10.1) |
| Idle baselines on WiFi (C1, C0, C1) | **done, 14 Oct 2026** (Section 10.2) |
| Link speed and R, load runs and the Go/No-Go decision | **not done** (M2 in progress; M6 later) |

**The one-sentence truth:** the *algorithm* exists and is tested; the *phone does not use it yet*, so there is no on-device evidence that it helps. (The wiring code now exists in the repository and the debug build passes on the Mac (15 Oct 2026), but it has never run on the phone; see Section 9.1.) Everything running on the phone today is NetValve plus our repairs.

## 2. Aim, feasibility and theory

- **Goal:** per-app tiers (High, Medium, Low; unticked apps stay outside the tunnel = Bypass) and a scheduler inside the VPN relay; measure the effect with a probe app (PayProbe) while a simulator app creates load.
- **Constraints:** zero budget, root-free, Android 10+ (API 29+), physical arm64 phone, Kotlin (Go only to build the netstack library).
- **Feasibility:** VpnService is the official root-free way to relay other apps' traffic; `getConnectionOwnerUid` (API 29+) maps connections to apps. The risk is engineering and measurement, not permission.
- **Theory in one paragraph.** Latency = propagation + transmission + queueing. When total load exceeds the bottleneck capacity, a queue builds in the router, access point or cell scheduler and every app waits in the same FIFO (bufferbloat). If the phone shapes its own traffic to R = 0.9 x measured goodput, the queue forms inside our pacer instead, where we choose the order: High first. A static cap on Low wastes the link while High is idle; a work-conserving scheduler does not.
- **Cannot do:** change the kernel queue discipline (root), make a router or tower favour us, control traffic that never enters the tunnel (Bypass apps, tethered clients, VoLTE/IMS), prioritise apps that refuse a VPN.

## 3. Positioning and scope decisions

1. **VPN must be on** for the scheduler to work. This is a design property (VpnService is the only root-free way) and is stated as a limitation.
2. **Banking and UPI apps** usually refuse a VPN. They are not a target. The case "video call plus banking app at once" is handled as **Bypass** (banking app outside the tunnel) and the benefit is **indirect**: because R is below link capacity, the shared bottleneck queue stays short. This is scenario S8 and is **unproven** (it also needs the banking app not to detect the VPN when excluded; untested on OxygenOS 16).
3. **Privacy VPNs (NordVPN style)** cannot run at the same time: Android allows one active VPN and ours is a VPN. Options: (A) document the limit and position the tool as a local alternative for people who want control rather than IP hiding (**chosen working assumption**); (B) future work: put the scheduler inside a WireGuard-style client. Decision to confirm with the course instructor.
4. **Claims.** Say *local and server-free*. Never say safe, secure or private: the relay forwards bytes unchanged, adds no encryption, hides no IP and does no TLS interception.
5. **Motivating examples** to use in the report and viva: calls, online exams, games and streaming versus background backup or update. Not banking.
6. **Open hypothesis.** Whether the benefit is large enough on the real phone is decided by the Go/No-Go experiment (roadmap M2). Until then neither proven nor killed.

## 4. Base project and code map

| Item | Value |
|---|---|
| Upstream | https://github.com/IEAmir/NetValve (Apache-2.0) |
| Our fork | https://github.com/Divyrajsinh-Parmar/NetValve |
| Pinned base commit | `f7b34b0` (tag `pinned-base`); we never track upstream |
| Local folder | `~/Downloads/qos-priority-scheduler`, branch `qos-scheduler` |

NetValve provides: per-app shaping via a local VPN, gVisor netstack (built by gomobile into `app/libs/netstack.aar`), token-bucket pacing both directions, UDP pacing, DNS exemption, UID attribution with an Unknown fallback, a TrafficModule plugin API, Compose UI, Room and DataStore. The default build uses a **loopback engine that does not forward traffic**; the real engine needs `-Pnetvalve.netstack=true` (arm64 only).

Paths under `app/src/main/kotlin/dev/netvalve/`:

| Path | Role |
|---|---|
| `throttle/ThrottleManager.kt` | `bucketFor(uid, direction)` and `pace(bucket, bytes)`; **the hot path we extend** |
| `throttle/TokenBucket.kt`, `PacingQueue.kt` | static caps; byte-bounded UDP queue (DROP_OLDEST, 256 KB) |
| `network/FlowSupervisor.kt` | `relayTcp`: pace before each write (about lines 83-109); `relayUdp`: queue then pace (about 156-190). **Four call sites to route through our pacer** |
| `network/UidResolver.kt` | attribution with cache, single-app inference, Unknown fallback |
| `network/ConnectionManager.kt` | protected upstream sockets |
| `service/NetValveVpnService.kt` | builds the TUN, allowed or disallowed app list, socket protector |
| `rules/`, `module/` | policy engine and TrafficModule hooks |
| `data/db`, `data/datastore`, `repository/` | Room (version 1), DataStore settings and app selection (ONLY_SELECTED or ALL_EXCEPT) |
| `ui/` | Compose screens (apps, dashboard, logs, stats, navigation) |
| **`scheduler/` (new, ours)** | pure-Kotlin scheduler, see Section 9 |
| **`payprobe/` (new, ours, separate module and APK)** | the measuring app: `Prober` (fresh HTTPS GET, whole-transaction timeout), `ProbeService` (foreground loop), `CsvSink`, `ProbeStats`, `ChartView`, `MainActivity` (code-built UI, no Compose, no libraries) |
| **`analysis/summarize.py` (new, ours)** | median, p95, p99, max, failures from PayProbe CSVs (standard library only) |

Upstream technical docs stay in `docs/` (ARCHITECTURE, EXTENDING, THROTTLING, DEBUG_THROTTLING, LIMITATIONS, BUILD_VERIFICATION, NETSTACK_EVIDENCE). The original README and handoff are in `docs/upstream/`.

## 5. Development environment

MacBook Air M1 (macOS). Test phone: **OnePlus Nord CE5, OxygenOS 16 (build 16.0.5.1002)**.

| Tool | Version | Note |
|---|---|---|
| Xcode Command Line Tools | installed | git, compilers (full Xcode not needed) |
| Homebrew | 6.0.20 | |
| JDK | Temurin 17.0.20.1 | keep 17; project verified on it |
| Go | 1.27.1 (`go.mod` needs 1.25+), `GOTOOLCHAIN=auto` | builds the AAR |
| Android Studio, SDK Platform 35 (plain, no ext), Build-Tools 35.0.0, Platform-Tools 37.0.1 | | |
| NDK | **27.1.12297006** (27.2 and 30.x also installed; 27.x is the verified family) | |
| Gradle | 8.10.2 via `./gradlew` | |

`~/.zshrc`:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export ANDROID_HOME=$HOME/Library/Android/sdk
export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/27.1.12297006
export PATH=$PATH:$ANDROID_HOME/platform-tools:$(go env GOPATH)/bin
export GOTOOLCHAIN=auto
```

`local.properties` (not committed): `sdk.dir=/Users/divyrajsinh/Library/Android/sdk`
Commands: `./gradlew test` · `./gradlew :app:assembleDebug -Pnetvalve.netstack=true` · `adb install -r app/build/outputs/apk/debug/app-debug.apk` · `adb logcat -d | grep -i netvalve`.
`gradle.properties` was retuned for a laptop (4 GB heap, daemon and parallel on). If a build runs out of memory: `git checkout pinned-base -- gradle.properties`.

## 6. Repository state and the package audit (12 Oct 2026)

The zip the student shared was **246 MB**: Finder had zipped `app/build` (230 MB), `.gradle`, `.kotlin`, `__MACOSX` and `.DS_Store`. The working tree was clean at commit `511c0de`. The cleaned package is about 13 MB including git history.

| Commit or tag | Meaning |
|---|---|
| `f7b34b0` / `pinned-base` | upstream state we forked |
| `a482a91` | restored manifest and resources, fixed `.gitignore`, executable scripts |
| `ea5f537` / `week1-aar-built` | netstack AAR, macOS fix in `build-aar.sh`, go.mod and go.sum |
| `511c0de` | first fix round and the v1 handoff report |
| `3df8724` / `scheduler-core` | cleanup, scheduler core, simulator, docs v2, build tuning |
| `5e49d1c` | docs: project now lives in ~/Downloads/qos-priority-scheduler |
| `40ec79d` | M0 done: handoff and tracker updated |
| `4e1b6da` / `m2-idle-baselines-mobile` | PayProbe module, `analysis/summarize.py`, first idle CSVs. (The tag name says M2, but the docs of that commit still described M1 as pending; fixed in the next commit.) |
| `c9c102b` | docs brought in line with the code; mobile idle runs archived (tag `m1-payprobe-done` points here, not at the PayProbe code commit `4e1b6da`) |
| `c34fc5f` | session log and start-of-session check added; branch and tags pushed to GitHub |
| `fb03ca0` | `tools/finish_session.sh` added |
| `478f4cd` | WiFi idle baselines (C1, C0, C1), Session 6 entry; pushed to GitHub |

Cleanup done: removed `README.fa.md`, `publish-to-github.sh` and the duplicate root `PROJECT_HANDOFF.md`; moved upstream `README.md`, `HANDOFF.md` and screenshots to `docs/upstream/`; new short `README.md`; new `NOTICE.md` (attribution and every changed file); `.gitignore` gained `.kotlin/` and `__MACOSX/`.
Committed on 12 Oct 2026.
**When zipping on the Mac use the terminal command in Section 7, not Finder.**

## 7. Setup and build log, and how to adopt this package

History (condensed): installed tools and SDK pieces; forked and pinned; `chmod +x` on scripts; first build failed on the missing manifest (Section 8, issue 2); built the AAR after the macOS fix; built the real-engine APK; authorised the phone for adb; installed; smoke test; fixed four reported problems.

**Adopt this package (also roadmap M0):**

```bash
cd ~/Downloads && mv qos-priority-scheduler qos-priority-scheduler.old
unzip ~/Downloads/qos-priority-scheduler-v2.zip -d ~/Downloads
cd qos-priority-scheduler
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew test
./gradlew :app:assembleDebug -Pnetvalve.netstack=true
git add -A && git commit -m "Cleanup, scheduler core with tests and simulator, docs v2"
```

**Make a clean zip (never Finder):**

```bash
cd ~/Downloads
zip -r ~/Desktop/qos-priority-scheduler.zip qos-priority-scheduler -x "*/.gradle/*" "*/build/*" "*/.kotlin/*" "*/.idea/*" "*/local.properties" "*.DS_Store"
```

## 8. Problems found and how they were solved

| # | Problem | Cause | Resolution |
|---|---|---|---|
| 1 | `permission denied: ./gradlew` | executable bit lost in upload | `chmod +x` (committed) |
| 2 | build: AndroidManifest.xml missing | upstream `.gitignore` contained `*.xml`, so the manifest and all resource XML were never uploaded | removed the line; recreated manifest and two resource files from what the code references (documented in `NOTICE.md`) |
| 3 | SDK Manager offered newer Build-Tools and NDK | defaults are latest | installed verified versions (Build-Tools 35.0.0, NDK 27.1) |
| 4 | `build-aar.sh`: gvisor rawfile build constraints | host check compiled for macOS; gVisor packages are Linux/Android only | check now uses `GOOS=linux GOARCH=arm64 CGO_ENABLED=0` |
| 5 | adb `unauthorized` on OnePlus | phone had not trusted the Mac | revoke authorisations, toggle USB debugging, replug unlocked, accept popup (Wireless debugging as plan B) |
| 6 | very slow Gradle builds | `gradle.properties` tuned for a 4 GB CI box | retuned for the laptop |
| 7 | Usage access list showed only system services | our recreated manifest omitted `PACKAGE_USAGE_STATS` | permission added |
| 8 | log: `protect() failed` for TCP | **hypothesis:** `java.net.Socket` creates its fd lazily, so `protect()` saw no valid fd | `bind` before `protect()`; student reports it works; confirm in logs when convenient |
| 9 | Dashboard tab dead after visiting Apps | bottom-bar navigation back to the start destination | `switchTab` helper |
| 10 | no app icons, confusing checkboxes | icons never loaded; meaning of a tick depends on selection mode | real icons, explanatory line, Tick all and Clear all |
| 12 | PayProbe closed immediately after tapping the icon | `window.insetsController` was called in `onCreate` **before** `setContentView`; the decor view does not exist yet, so Android threw a NullPointerException (`MainActivity.kt:50`). Compiling and JVM tests cannot catch this | call moved after `setContentView` and wrapped in `runCatching`; found with `adb logcat -b crash -d` |
| 11 | **round-robin was unfair** (one flow got 8 MB, another 0.6 MB in the same tier) | a tiny floor grant used up a flow's turn | fixed in `PriorityPacerCore.serve`; found by the new unit tests |
| 13 | First probes after pressing Start sometimes all time out (two 3-row CSVs of 5000 ms rows) | Hypothesis: the tunnel was not usable yet. Procedure now: start the VPN, wait about 10 seconds, then Start PayProbe; if the first rows are timeouts, Stop and redo the run under a new label | open; **checked 14 Oct 2026 (Session 7):** the first 10 samples of all three WiFi runs are `ok=1` (no timeouts); the only timeouts in the repo are the two 3-row mobile files, so the cause (tunnel not ready) is still a hypothesis |
| 14 | The VPN-off (C0) idle run is missing from the data; two files labelled C1 were reported as C0 | C0 is repeated; check the VPN key icon is gone before pressing Start, and put C0 or C1 in the label | **closed 14 Oct 2026:** C0 run recorded (`C0_idle_wifi_r1`) |
| 15 | The planned way to put the in-app simulator under the scheduler (tick NetValve in the Apps list) could not work | `RuleEngine.isControlled` returned false for our own UID ("never shape ourselves"), so our own flows were relayed unshaped and skipped the scheduler (found 14 Oct 2026 by reading the code) | changed: our own UID counts as controlled only when NetValve is ticked in ONLY_SELECTED mode (ALL_EXCEPT still excludes it). Code written, not built, not tested on the phone |
| 16 | `./gradlew test` failed in `:payprobe:compileDebugUnitTestKotlin` (found 15 Oct 2026 from the Mac output) | `PayProbeCoreTest.kt` imported the JDK class `com.sun.net.httpserver.HttpServer`, which is not on the Android Gradle unit-test classpath; the 9 PayProbe tests had therefore only ever run in a sandbox, never under Gradle | test server rewritten on a plain `java.net.ServerSocket` (`TinyHttpServer`); 9 of 9 pass in a sandbox in 3 runs with a stand-in runner. Gradle result 15 Oct 2026 (Mac): the tests compile and 8 of 9 pass; `hungServerIsCutOffAtTheTimeoutAndStoredAsTheCap` failed on its note check. `Prober` now stores every kind of timeout (hard cap, connect, read) as "timeout" at the cap, and a new test covers it; Gradle result: `./gradlew test --continue` ended with BUILD SUCCESSFUL on the Mac (15 Oct 2026, third run) |
| 17 | `TrafficSimulatorTest.downloadUsesThreeParallelConnectionsAndCountsBytes` failed under Gradle with `NoSuchElementException` at the `sim.stop()` line (15 Oct 2026, Mac output) | `TrafficSimulator.stopInternal` called `sockets.toList()` on a concurrent set while worker threads removed their sockets; Kotlin's `toList()` can throw `NoSuchElementException` in that moment (a small sandbox demo gave 38,489 failures in 2 million tries, none with a plain loop). It is a real bug that could hit the Stop button. The stack trace itself was not seen, so the link to this exact line is likely, not proven | changed to a plain `for` loop and added a 100-round start/stop stress test; 63 of 63 pure tests pass in the sandbox in 3 runs (the race itself could not be reproduced in the simulator there). Gradle result: `./gradlew test --continue` ended with BUILD SUCCESSFUL on the Mac (15 Oct 2026, third run) |
| 18 | Cosmetic: the bottom-bar label "Dashboard" wraps onto two lines ("Dashboa" / "rd") on the phone | seen in the student's screenshots on 16 Oct 2026; likely because the Load tab made it five tabs (not checked in the code) | label renamed to "Home" in the code (Session 11); seen on the phone in the screenshots of 17 Oct 2026 (Session 12): "Home" is on one line |
| 19 | Cosmetic, from the student's screenshots of 17 Oct 2026: (a) the setup checklist said "Apps are chosen" with only PayProbe ticked, although the Load test needs NetValve ticked too; (b) the PayProbe fields lost their hints once filled, so "90" had no unit; (c) the PayProbe status line showed "p99 0 ms, max 0 ms, mean 0 ms" with no samples | (a) the checklist only counted ticked apps; (b) the fields used `hint` only; (c) the line was formatted without a samples check | Session 12 code: checklist warns "Also tick NetValve (for the Load test)" in only-selected mode; PayProbe has a label above each field; the status line shows "-" with no samples; 2 new checklist tests. Not built in the AI sandbox. The low-contrast OFF switch is only noted, not changed |
| 20 | Cosmetic: on the Load tab the "Both" button label wrapped onto two lines ("Bo" / "th") (student's screenshot, 17 Oct 2026) | three buttons in one row with the default button padding left too little width for the third | Session 12 (second part): narrower button padding and one-line labels in `SimulatorScreen`; not built in the AI sandbox |
| 21 | The Home card "Optional: usage access" kept showing although usage access was already granted to NetValve (student's report, 17 Oct 2026) | The card read `DeviceState.usageAccessGranted`, which defaults to false and is only filled while the tunnel runs (the device monitor starts with the engine); also the app-op alone can return MODE_DEFAULT on some phones (hypothesis for the second part) | Session 12 (third part): `UsageAccess.isGranted` reads the app-op and, on MODE_DEFAULT, the permission; the Home screen reads it itself every 2 s (`DashboardUiState.usageAccessGranted`). Not built in the AI sandbox, no unit test (needs the Android framework) |
| 22 | Load tab runs of 20 s showed a very small "Total down" (64.8 KB with the tunnel OFF on the hotspot, 85.3 KB with Protection ON on mobile data) and Errors 0 (student's screenshots, 17/18 Oct 2026) | the download loop counted every byte of any answer and treated a closed connection as a success; the status line of the answer was never read. Hypothesis, not shown: the server answers with a small non-200 page, or the transfer stalls | Session 12 (fourth part): the first status line is checked (not 200 or 206 gives the error "HTTP status: ..."); an answer with no data gives an error; 2 new tests. Not built in the AI sandbox. The real cause of the small totals is open until the next run |
| 23 | The Load tab got "HTTP/1.1 403 Forbidden" from `speed.cloudflare.com/__down?bytes=100000000` on mobile data with Protection ON (87 errors, 0 B; student's screenshot 02:35, 18 Oct 2026) | the server rejected our request. Hypothesis (not shown): Cloudflare's bot check on the unknown User-Agent `NetValve-Sim`. The same run proves that the relay forwards TLS over IPv4 (the answer came back through the tunnel); with `http://speedtest.tele2.net/100MB.zip` the same setup gave 5.4 Mbps average, 12.8 MB in 20 s, Errors 0 (screenshot 02:41), but a far server may not fill a fast link | Session 12 (fifth part): User-Agent `curl/8.5.0`, and the first characters of the answer body are added to the error ("HTTP status: ... | error code: 1010"); 1 new test. Not built in the AI sandbox; whether Cloudflare now accepts the request is open |

## 9. What we built: the scheduler (package `dev.netvalve.scheduler`)

Pure Kotlin, no Android imports, deterministic (injected clock), so it is testable in milliseconds.

| File | Purpose |
|---|---|
| `SchedulerModel.kt` | `Tier` (HIGH, MEDIUM, LOW; max 3 High apps), `SchedMode` (OFF, ADAPTIVE_THROTTLE, PRIORITY_PACER), `SchedulerConfig` (validated parameters, one per direction) |
| `PriorityPacerCore.kt` | **Mode B** state machine: floors, strict priority, caps while High is active, round-robin per tier, capped banked budget |
| `AdaptiveThrottle.kt` | **Mode A** rule: while High moved bytes in the last `holdMs`, cap Medium and Low at alpha x R |
| `CalibratorMath.kt` | R = 0.9 x steady goodput after discarding the slow-start window |
| `SchedulerPacer.kt` | coroutine adapter: `acquire(tier, flowId, lane, bytes)` suspends until granted; one pump per lane; sleeps when idle; removes a cancelled request |
| `TierAssignments.kt` | tier rules: max 3 High (locked entries count), locked entries, default Medium, shared-UID takes the highest tier, snapshot for persistence |

**Parameters (defaults):** tick 10 ms, grant 4096 B, hold 2000 ms, alphaMedium 0.50, alphaLow 0.20, floorMedium 0.10, floorLow 0.05.

**Mode B per tick:** (1) budget = min(carry + R x dt, 2 x R x tick); (2) floors for waiting Medium and Low; (3) strict priority for the rest: High, Medium, Low, round-robin across flows, at most one grant per turn; (4) while High is active Medium is capped at 50 percent and Low at 20 percent of R, otherwise no caps; (5) leftover is carried but never beyond the cap. Expected extra delay for High is about tick + grant/R plus kernel-buffer residue (to be measured, not assumed).

**Tests (new, 36):** `PriorityPacerCoreTest` (work conservation, rate limit, strict priority shares, no starvation over 60 simulated seconds, floors ablation, caps only while High active, no caps after hold, grant size, round-robin fairness, banked-budget cap, in-place rate change, cancel, High overtakes saturating Low), `SchedulerSupportTest`, `TierAssignmentsTest`, `SchedulerPacerTest` (real-time, generous tolerances), `SchedulerSimulationTest`. Existing upstream suites: 7.

### 9.1 M3 to M5 code (written 14 Oct 2026, plan change: build first, test afterwards)

**Plan change (student's decision, 14 Oct 2026):** only a few days are left, so the infrastructure (M3 wiring, M4 tier UI, M5 simulator) is built first; testing, M2 measurements and fixes follow. M2 and M6 are run together later on the hotspot network (the network of the earlier WiFi idle runs is unknown). Risk accepted: if the phone effect turns out small, the M3 to M5 effort was spent before knowing.

**Evidence level (read this before trusting the code).**
- Pure Kotlin (`scheduler/` and `sim/`) compiled with kotlinc 2.0.21 with warnings as errors, and **62 JVM tests passed** (the 36 old ones plus 26 new) in an AI sandbox using a stand-in test runner, not Gradle. The traffic simulator was exercised only against a local server on a JVM.
- Android-dependent code (Hilt modules, DataStore, Compose screens, `FlowSupervisor`, `RuleEngine`, `TrafficEngine`): `./gradlew :app:assembleDebug -Pnetvalve.netstack=true` and `:payprobe:assembleDebug` **passed on the student's Mac on 15 Oct 2026** (Session 9 check output, run with `-q`). Unit tests under Gradle: the second run on 15 Oct 2026 had 96 app tests, 95 passed and 1 failed (simulator stop race, problem 17); after the fixes for problems 16 and 17 the third run, `./gradlew test --continue`, ended with BUILD SUCCESSFUL (the pasted output showed only the end, so the exact test counts of that run were not seen). First phone check, reported by the student on 15 Oct 2026 (no screenshot or output): after a fresh install of both APKs the Dashboard shows the scheduler card OFF with R = 0, the Apps list shows NetValve and PayProbe, PayProbe shows a fixed High chip when ticked, the VPN permission popup and key icon appear after Start, and browsing works with the tunnel on. Not verified: scheduler ON, tier persistence, the Load tab, and all measurements.

**New pure Kotlin (package `scheduler`, no Android imports).** `SchedulerSettings` and `RateUnits` (Mbps to bytes per second, parsing and formatting), `TierResolver` (uid to tier, cached, generation counter so a stale lookup cannot be stored after a tier change), `SchedulerController` (the one object the relay calls: `newFlowId`, `isActive`, `acquire`, `releaseFlow`, `describeFlow`; follows the settings flow; mode OFF or rate 0 returns at once). `TierAssignments` methods are now `@Synchronized` (relay threads read while the UI writes). Differs from the roadmap text: one `SchedulerController` instead of passing a pacer and a resolver separately to `FlowSupervisor`.

**New Android code.** `TierRepository` and `DataStoreTierRepository` (DataStore key `tiers_json`, no Room), `TierStore` (own package Low through `context.packageName`, `dev.netvalve.payprobe` High, saved tiers load asynchronously, until then everything is Medium), providers in `EngineModule` and `RepositoryModule`, `GlobalSettings` gained `schedMode`, `schedRateDownBytesPerSec`, `schedRateUpBytesPerSec` (defaults keep old stored JSON readable), `TrafficEngine` passes the controller to `FlowSupervisor` (new LAST constructor parameter, default null, so the instrumented test still compiles).

**Relay (`FlowSupervisor`).** At the four sites `sched?.acquire(uid, flowId, lane, bytes)` runs before `throttleManager.pace(...)`, so static caps still apply on top. Flows that are paused, not controlled, or DNS-exempt do not take part. TCP checks the mode on every chunk. UDP upload is routed through the pacing queue only if the scheduler is on for upload when the flow opens (a mode change reaches UDP flows opened afterwards). `releaseFlow` runs in both `finally` blocks. A debug log line per flow shows tier, mode and R. Decision (roadmap item 7): UDP keeps `DROP_OLDEST`. Not done: smaller upstream socket buffers (roadmap risk note).

**UI.** Dashboard card "Priority scheduler" (switch, R download and R upload in Mbps, Save rates). Apps screen: tier chip on controlled apps (tap: Medium, High, Low, Medium), High holds at most 3 apps and a fourth goes to Low with an explanation, fixed chips for our app (Low) and PayProbe (High), "Not controlled" label otherwise. New bottom tab "Load": the traffic simulator (3 parallel downloads, 2 parallel uploads, each a separate HTTP/1.1 connection over a raw socket, live rates and totals, auto-stop default 60 s). Default endpoints are `speed.cloudflare.com` `__down` and `__up`; whether `__up` accepts the 25 MB POST bodies is **not verified**. The simulator runs under our own UID, so NetValve itself must be ticked in the Apps list in ONLY_SELECTED mode.

**New files.** `scheduler/SchedulerSettings.kt`, `TierResolver.kt`, `SchedulerController.kt`, `repository/TierRepository.kt`, `service/TierStore.kt`, `sim/SimHttp.kt`, `sim/TrafficSimulator.kt`, `ui/sim/SimulatorScreen.kt`, `ui/sim/SimulatorViewModel.kt`; tests `RateUnitsTest`, `TierResolverTest`, `SchedulerControllerTest`, `TrafficSimulatorTest`. Changed: `GlobalSettings`, `RuleEngine`, `FlowSupervisor`, `TrafficEngine`, `EngineModule`, `RepositoryModule`, `TierAssignments`, `AppSelectionViewModel`, `AppsScreen`, `DashboardViewModel`, `DashboardScreen`, `Navigation`.

**Not verified yet:** Gradle build and KSP/Hilt wiring; DataStore persistence of tiers; that Mode OFF behaves exactly like before on the phone; chip behaviour and the High limit in the UI; the simulator on a real network and its traffic appearing as our own app and being paced as Low; whether Mode B lowers PayProbe p95 (that is the M2/M6 question).

### 9.2 Names, and the Home and PayProbe screens (Session 11, 16 Oct 2026; built on the Mac, student-reported; Home and PayProbe screens seen in screenshots on 17 Oct 2026, Load and Apps tabs not yet)

**Names.** The student found "Priority scheduler (Mode B)" confusing. Decision: the app says **Priority Control**; the report says **priority pacer (Mode B)** so it stays distinct from the simulation-only Mode A. R is called **Shared speed** in the app. The tabs are Home (was Dashboard, which wrapped onto two lines), Apps, Stats, Load, Logs.

| In the app | In the report |
|---|---|
| Priority Control | priority pacer, Mode B |
| Shared speed | rate R (set to about 0.9 x the measured link speed) |
| High / Medium / Low | tiers |
| Load tab | in-app traffic simulator (the "Low app") |

**Home screen (redesigned).** One big Start or Stop protection button with live download, upload and connections; a setup checklist (tunnel running, only picked apps controlled, apps ticked, shared speed set, Priority Control ON) whose rules are in the pure class `SetupChecklist` and are unit tested; a Priority Control card (switch, plain explanation, measured speed to "Use 90% as shared speed", exact values under an advanced button); controlled apps shown with their tier; tunnel settings in plain words. The Load tab now also shows speed in Mbps and the average of the run, to make the link-speed measurement easy. **PayProbe screen (redesigned):** how-to box, run name with "Saved as" preview, run length in seconds (the service stops by itself, so repeated runs are equally long), coloured status, big numbers (p50, p95, failures, samples), chart, file path. `analysis/summarize.py` gained `--from-sec` and `--to-sec` (cut by elapsed time).

**Evidence level:** the pure Kotlin parts (checklist, 90% helper, Mbps helpers) compiled and 74 JVM tests passed in an AI sandbox with a stand-in runner; `summarize.py --from-sec 10 --to-sec 70` gave 60 rows on a real CSV, equal to an independent count, and `--skip 10` still gives the numbers of handoff 10.2. The Compose and View code was only syntax-checked: it has not been built with Gradle, never run on the phone, and its look has not been seen. The step-by-step experiment guide is `docs/HOW_TO_RUN_EXPERIMENT.md`.

## 10. Results so far (simulation, plus first phone baselines)

**Model.** One 5 Mbps bottleneck with a 256 KB FIFO (about 410 ms of queue), base RTT 20 ms. A High app sends a 1.4 KB request every 200 ms in 10 s bursts separated by 10 s of silence; Low is three greedy downloads. R = 0.9 x link. Simplifications: no TCP back-off (the standing queue is what matters), measured High requests are never dropped (this *understates* unscheduled latency). Reproduce: `./gradlew test` prints the tables; CSV in `docs/data/sim_results.csv`.

**S1, latency of the High request (ms) and Low goodput:**

| Config | p50 | p95 | p99 / max | Low goodput | Low while High idle |
|---|---|---|---|---|---|
| C1 scheduling OFF | 440 | 441 | 441 / 441 | 622 kB/s | 625 kB/s |
| C2 static cap R/5 | 23 | 23 | 23 / 23 | 113 kB/s | 113 kB/s |
| C3 Mode A | 23 | 23 | **441 / 441** | 331 kB/s | 533 kB/s |
| **C4 Mode B** | **23** | **23** | **23 / 23** | 297 kB/s | **482 kB/s** |

**S4 (High never sends):** C4 Low goodput 562 kB/s = **100 percent of R** and 90 percent of the unscheduled link (the roadmap's expected 10 percent cost); C2 only 113 kB/s.
**S5 (High saturates the link):** Low still gets 28.9 kB/s (**5.1 percent of R**) with floors, but 1.0 kB/s (0.2 percent) without them.

**What this supports (at model level only):**
1. Without scheduling, a High request waits behind the full queue (bufferbloat, about 440 ms). Mode B keeps it at the idle-link value (23 ms).
2. A static cap gives the same latency but throws the link away: Low gets 113 kB/s even while High is idle, versus 482 to 562 kB/s with Mode B. This is the main novelty claim versus the static caps NetValve already has.
3. Mode A is reactive: typical latency is fine but the first request after an idle period meets the queue Low built (p99 and max 441 ms). Mode B has no such spike.
4. The hold time costs idle-window throughput: Low reaches 86 percent of R in the High-idle windows of S1 because it is still capped for 2 s after High stops.
5. Floors are what prevent starvation (C4 versus C5).

**What it does NOT show:** any behaviour of the real phone, WiFi, cellular, TCP dynamics, kernel buffers or downlink lag. Those are the job of milestones M2 and M6. If the phone shows a weak effect, these simulation results remain the algorithm contribution (Plan C is already substantially met).

**Verification status.** Verified in a sandbox with Kotlin 2.0.21, JUnit 4.13.2, JDK 21 and the coroutines jar shipped in the Kotlin distribution: all 36 tests pass and the scheduler sources compile with warnings as errors. **PayProbe (12 Oct 2026):** all Kotlin sources compile against the Android API 35 `android.jar`; 9 JVM tests pass, including probes against a local HTTP server (204, 500, refused connection, a hung server cut off at the timeout and stored as the cap), CSV escaping and flushing, and the percentile maths. The Gradle build of `payprobe` succeeded on the Mac. The first run on the phone crashed on start (problem 12 in Section 8, now fixed); after the fix the acceptance runs passed on 14 Oct 2026 (CSVs written, pulled with `adb pull`, summarised by `analysis/summarize.py`).

**Also verified on the Mac (12 Oct 2026, milestone M0):** `./gradlew test` and the real-engine APK build succeed, and all 36 new tests pass under Gradle. **On the phone (14 Oct 2026):** PayProbe runs and writes CSVs. **Still not on the phone:** the scheduler itself (M3 is not started).

### 10.1 Idle baselines on mobile data (14 Oct 2026, phone, no load)

<!-- docs-update-2026-10-14 -->
Setup: mobile data on, WiFi off, phone untouched, about 3 minutes each, first 10 samples skipped, VPN **on** (NetValve running; PayProbe plus five other apps ticked, all idle). Values in ms:

| File (label in the CSV) | n | fails | p50 | p95 | p99 | max |
|---|---|---|---|---|---|---|
| `C1_idle_mobile_r1` (first run) | 187 | 0 | 200 | 246 | 288 | 366 |
| `C1_idle_mobile_r1` (second file, same label) | 174 | 0 | 208 | 278 | 337 | 341 |
| `C1_idle_mobile_r2` | 171 | 0 | 204 | 350 | 1124 | 1225 |

Reading: (1) these are **three runs in the same condition (VPN on, idle)**, and their p95 values spread from 246 to 350 ms. The noise floor on mobile data is therefore about 100 ms at p95, and any later effect must be clearly bigger than that, must use medians over at least 5 runs, and must not rely on p99 or max. (2) The medians are 200 to 208 ms, so there is no sign of a problem with the tunnel itself. (3) **VPN overhead is NOT measured yet:** no VPN-off (C0) run exists in the data. An earlier chat summary called the two pooled `r1` files "C0"; that was wrong (both files are labelled C1) and is withdrawn. (4) The smoke run `test` (234 samples, p50 204, p95 259, max 1562) is not a baseline.

Data hygiene found while checking the CSVs: two 3-row files (`C1_idle_mobile_r0`, and a second `C1_idle_mobile_r1`) contain only 5000 ms timeouts, from starts where the tunnel was probably not ready yet (hypothesis, not confirmed). `--skip 10` hides them, but they are kept out of future analysis (always run `summarize.py` with `--skip 10`: with `--by label` and no skip the two files are pooled into `C1_idle_mobile_r1`, giving 384 rows, 3 failures and max 5000 ms; checked 14 Oct 2026). These runs live in `docs/data/` (mobile-data folder) as noise-floor evidence; the baselines are being redone on the network that the load runs will use.

### 10.2 Idle baselines on WiFi (14 Oct 2026, phone, no load)

Setup as instructed: WiFi on, mobile data off, phone untouched, 3 minutes each, first 10 samples skipped, only PayProbe ticked in NetValve; VPN on for C1 and off for C0 (the student was asked to confirm the VPN key icon was gone for C0). WiFi name, band and signal were not recorded in the repo, and the student cannot remember which network it was (asked 14 Oct 2026), so these three runs count as **network unknown** and are repeated on the load network. Files: `docs/data/idle_wifi/files/`. Values in ms:

| Run | VPN | n | fails | p50 | p95 | p99 | max | mean |
|---|---|---|---|---|---|---|---|---|
| `C1_idle_wifi_r1` | on | 173 | 0 | 193 | 334 | 1164 | 1548 | 219 |
| `C0_idle_wifi_r1` | off | 172 | 0 | 214 | 276 | 366 | 401 | 215 |
| `C1_idle_wifi_r2` | on | 172 | 0 | 202 | 268 | 419 | 423 | 205 |

Reading: (1) **No sign that the tunnel adds latency at idle.** The VPN-on medians (193 and 202) are 12 to 21 ms *lower* than the single VPN-off median (214), but the two VPN-on runs differ from each other by 9 ms and the means are close (215 versus 219 and 205). This is not evidence that the VPN is faster; with one C0 run the honest statement is "no measurable overhead". This replaces point (3) of Section 10.1 (no C0 run existed then). The overhead gets a proper number in M6, with at least 5 runs per condition. (2) **Noise floor on WiFi:** two identical VPN-on runs differ by 66 ms at p95 (268 versus 334) and one run has a short slow patch (p99 1164, max 1548). Medians spread by about 21 ms. A later effect must be far larger than that, must use medians over at least 5 runs, and must not rely on p99 or max. (3) WiFi did not give a lower median than mobile data in these runs (mobile 200 to 208, WiFi 193 to 214); the probe is a fresh HTTPS request to a remote server every second, so the whole transaction time, not only the local link, is measured.

### 10.3 Smoke runs, scheduler OFF, tunnel ON (16 Oct 2026, phone, PayProbe only, no load)

Source: the student's screenshots (`SMOKE_TEST.pdf`, not in the repo) and `analysis/summarize.py --skip 10` on `docs/data/smoke_2026-10-16/`. One run of about 90 s per network, 0 failures in each. These are smoke checks, not measurements: one run each, different networks, so they say nothing about the effect of the tunnel or the scheduler.

| Label as stored | Network | On-screen (all samples): n, p50, p95 | `--skip 10`: n, p50, p95, p99, max, mean |
|---|---|---|---|
| `SMOKE_off__mobile_r1` | mobile data (5G) | 91, 243, 318 | 81, 242, 292, 458, 458, 246 |
| `SMOKE_off__Wifi_r1` | WiFi "GUEST" (student: college WiFi) | 91, 115, 324 | 81, 117, 324, 473, 473, 133 |
| `SMOKE_off__hotspot_r1` | test phone as hotspot host, 1 device connected at set-up (not the planned setup) | 92, 222, 265 | 82, 223, 259, 347, 347, 221 |

Observations (hypotheses, not conclusions): the mobile p50 is about 35 ms above the three C1 mobile runs of 14 Oct (200, 208, 204 ms); the WiFi run has a much lower p50 but a spiky tail (p95 324, max 473). The p50 of the WiFi runs of 14 Oct (about 193 to 214 ms) is far from today's 115 ms, which fits the fact that the network of the 14 Oct WiFi runs is unknown.

## 11. Remaining work (summary; details in the roadmap)

M0 adopt and verify (done) · M1 PayProbe (done) · M2 Go/No-Go experiment (in progress: idle baselines done on mobile data and WiFi; link speed, R and load runs pending) · M3 wire the scheduler into the relay, M4 tier UI and M5 in-app traffic simulator (code written 14 Oct 2026, debug build passes on the Mac 15 Oct, not run on the phone, Section 9.1) · M6 evaluation on the phone · M7 optional stretch (on-device calibration, Mode A on device, graph, automation) · M8 report, viva, demo. The fast path is M0, M1, M2, M3, M4, M5, M6, M8.

## 12. Risks and limitations to remember

- One VPN at a time; starting ours revokes another; Android 16 (our phone runs OxygenOS 16): stop the VPN before reinstalling the APK or the internet can break until restart.
- Only tunnelled traffic is controlled; congestion caused by other people can only be softened by lowering our own load.
- A wrong R removes the benefit; control costs about 10 percent peak throughput plus CPU and battery.
- No AQM (no CoDel-style drop); TCP is controlled by back-pressure; downlink control is indirect and lags.
- UDP and QUIC attribution depends on kernel support; the base is young with a single maintainer (pinned fork).
- OnePlus quirks: adb authorisation popup; if installs complain about permissions try Developer options, Disable permission monitoring.
- A fixed-R demo can look rigged: also show calibrated mode, a real-app scenario (S6) and the paused-app bound (C6).

## 13. Instructions for the AI reading this

- The student is a **beginner**. Give small numbered copy-paste steps, one stage at a time, and ask for the output before moving on.
- Explain ideas in **Hinglish, told like a short story or analogy**, then give exact commands.
- Verify claims against the actual code; say clearly when something is a hypothesis or untested.
- New logic goes in `dev.netvalve.scheduler` (pure Kotlin, tested). Never rewrite the relay or the netstack. Keep upstream edits tiny and list them in `NOTICE.md`.
- Do not use `removeFirst()` or `removeLast()` on `MutableList` or `java.util` collections (on JDK 21 they bind to Java methods missing on older Android). `kotlin.collections.ArrayDeque` is fine.
- Ask for the full error text (Gradle: the "What went wrong" block). Stay honest about limits.
- Machine: MacBook Air M1, folder `~/Downloads/qos-priority-scheduler`, phone OnePlus Nord CE5 (OxygenOS 16).

## 14. Glossary

**VpnService** official API for a local VPN · **netstack (gVisor)** user-space TCP/IP stack · **AAR** Android library archive built from Go by gomobile · **token bucket** allows bytes at a set rate · **work-conserving** never leaves capacity idle while someone waits · **bufferbloat** large queues that add delay · **UID** Android's per-app identity (priority is per UID, hence a separate PayProbe APK) · **Bypass** app left outside the tunnel · **pinned commit** fixed upstream version · **floor** guaranteed minimum share · **hold** time a High app still counts as active.

## 15. Change log

| Date | Change | Sections |
|---|---|---|
| 7 Oct 2026 | v1.0 created from the setup session; phone recorded; scope questions (banking, privacy VPN) and positioning rules recorded | all |
| 7 Oct 2026 | First fix round built and reported working (usage access, protect(), Dashboard tab, app icons) | 8, 10 |
| 12 Oct 2026 | **v2.0.** Package audited (246 MB to 13 MB); upstream clutter removed or moved; NOTICE.md and new README; `gradle.properties` retuned | 6, 7 |
| 12 Oct 2026 | Scheduler core (Mode B, Mode A rule, calibration maths, coroutine adapter, tier rules) written and verified with 36 JVM tests; fairness bug found and fixed | 9, 8 |
| 12 Oct 2026 | Bottleneck simulation of C1 to C5 added; results recorded; roadmap rewritten as dependency-ordered milestones (no calendar weeks) | 10, 11 |
| 12 Oct 2026 | **M0 done.** Package adopted at ~/Downloads/qos-priority-scheduler; 36 scheduler tests pass under Gradle; committed (3df8724, tag scheduler-core); old folders removed | 6, 10 |
| 12 Oct 2026 | **M1 code written.** `payprobe/` module (separate APK `dev.netvalve.payprobe`), CSV format, `analysis/summarize.py`; compile-checked against android.jar API 35, 9 JVM tests pass; phone run pending | 1, 4, 6, 10, 11 |
| 13 Oct 2026 | PayProbe first-launch crash found on the phone (insets controller used before `setContentView`) and fixed; lesson recorded: always read `adb logcat -b crash` after a first on-device run | 8, 10 |
| 14 Oct 2026 | **M1 done.** PayProbe runs on the phone; first smoke run and idle runs on mobile data recorded; commit `4e1b6da` | 8, 10 |
| 14 Oct 2026 | Data check found no VPN-off (C0) run and two junk files; docs and code reconciled; idle baselines to be redone; M2 started | 8, 10, 11 |
| 14 Oct 2026 | Session log (`docs/SESSION_LOG.md`) and start-of-session check (`tools/session_start_check.sh`) introduced; sessions 1 to 5 reconstructed from git history | 0, 15 |
| 14 Oct 2026 | Branch `qos-scheduler` and 5 tags pushed to GitHub (`c34fc5f`); `tools/finish_session.sh` added (commit and push only if session log and handoff are updated) | 6, 15 |
| 14 Oct 2026 | **Idle baselines on WiFi (C1, C0, C1) recorded:** no measurable VPN overhead at idle; WiFi p95 noise about 66 ms; problem 14 closed | 8, 10, 11 |
| 14 Oct 2026 | Session 7 entry check: tables 10.1 and 10.2 recomputed from the CSVs (they match); problem 13 first-10-samples check; `--skip 10` rule; Section 6 commit rows corrected | 6, 8, 10 |
| 14 Oct 2026 | Network of the WiFi idle runs is unknown (not recoverable); they are repeated on the load network | 10 |
| 14 Oct 2026 | Plan change: M3, M4, M5 code written before M2 and M6 testing; Section 9.1 added; problem 15 (own UID never controlled) added | 1, 8, 9, 11 |
| 14 Oct 2026 | `docs/REMAINING_WORK.md` added (status, remaining work, report update list, prompts) | 15 |
| 15 Oct 2026 | Session 9: first full Gradle check; debug builds pass, `./gradlew test` failed in PayProbe tests (problem 16); status wording for M3 to M5 corrected | 1, 8, 9, 11 |
| 15 Oct 2026 | Session 9 (Mac result): PayProbe tests compile, 8 of 9 pass; `Prober` timeout notes made consistent (problem 16) | 8 |
| 15 Oct 2026 | Session 9 (second Mac result): no PayProbe test failure reported; app unit tests 96 ran, 95 passed; simulator stop race fixed (problem 17) | 8, 9 |
| 15 Oct 2026 | Session 9 (third Mac result): `./gradlew test --continue` BUILD SUCCESSFUL; problems 16 and 17 closed | 8, 9 |
| 15 Oct 2026 | Session 10: first phone check (fresh install, first open, tunnel start), student-reported | 9 |
| 16 Oct 2026 | Session 10: three smoke runs with the scheduler OFF (mobile, WiFi, hotspot host) recorded in Section 10.3; problem 18 (cosmetic) added | 8, 10 |
| 16 Oct 2026 | Session 11: names (Priority Control), Home and PayProbe screens redesigned (code only), summarize.py time window, experiment guide; Section 9.2 added | 8, 9 |
| 15 Oct 2026 | One-time patch scripts of Sessions 7 to 9 archived in `tools/patches/` | 15 |
| 17 Oct 2026 | Session 12: Home and PayProbe screens seen in screenshots; small fixes (problem 19): checklist NetValve tick warning, PayProbe field labels, status line without samples | 8, 9.2 |
| 17 Oct 2026 | Session 12 (second part): Load tab seen on the phone; "Both" button fix (problem 20) | 8 |
| 18 Oct 2026 | Session 12 (third part): usage-access card fix (problem 21) | 8 |
| 18 Oct 2026 | Session 12 (fourth part): Load tab status check (problem 22) | 8 |
| 18 Oct 2026 | Session 12 (fifth part): Load tab 403 from Cloudflare, User-Agent change (problem 23) | 8 |
