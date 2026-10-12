# Priority-Aware Traffic Scheduler for Android: Project Handoff Report

**Version 2.0 · 12 Oct 2026** · Author: Divyrajsinh Parmar (Computer Engineering, IITRAM Ahmedabad) · Course: Computer Networks (college project)
Companion file: `docs/IMPLEMENTATION_ROADMAP.md` (what is left and how to build it). This file says where the project IS.

## 0. How to use this document

Give this file and `docs/IMPLEMENTATION_ROADMAP.md` (or simply the repo zip, which contains both) to a new AI chat with this message:

> Read docs/PROJECT_HANDOFF.md and docs/IMPLEMENTATION_ROADMAP.md fully. They are my project history, current state and plan. Continue with the first unfinished milestone in the roadmap tracker. Follow Section 13 of the handoff on how to talk to me.

**After every finished task** the AI must update (a) the change log (Section 15) and any section whose facts changed, (b) the tracker in the roadmap, and re-create the zip. These two files are the single source of truth; copies elsewhere are stale.

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
| Scheduler wired into the relay (`FlowSupervisor`) and switchable in the app | **not done** (milestone M3) |
| PayProbe (the measuring app), in-app traffic simulator, tier UI | **not done** (M1, M4, M5) |
| On-phone measurements and the Go/No-Go decision | **not done** (M2, M6) |

**The one-sentence truth:** the *algorithm* exists and is tested; the *phone does not use it yet*, so there is no on-device evidence that it helps. Everything running on the phone today is NetValve plus our repairs.

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
| Local folder | `~/projects/qos-priority-scheduler`, branch `qos-scheduler` |

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
| **staged, not committed (this package)** | cleanup, scheduler core, simulator, docs, build tuning |

Cleanup done: removed `README.fa.md`, `publish-to-github.sh` and the duplicate root `PROJECT_HANDOFF.md`; moved upstream `README.md`, `HANDOFF.md` and screenshots to `docs/upstream/`; new short `README.md`; new `NOTICE.md` (attribution and every changed file); `.gitignore` gained `.kotlin/` and `__MACOSX/`.
To commit: `git add -A && git commit -m "Cleanup, scheduler core with tests and simulator, docs v2"` then `git tag scheduler-core`.
**When zipping on the Mac use the terminal command in Section 7, not Finder.**

## 7. Setup and build log, and how to adopt this package

History (condensed): installed tools and SDK pieces; forked and pinned; `chmod +x` on scripts; first build failed on the missing manifest (Section 8, issue 2); built the AAR after the macOS fix; built the real-engine APK; authorised the phone for adb; installed; smoke test; fixed four reported problems.

**Adopt this package (also roadmap M0):**

```bash
cd ~/projects && mv qos-priority-scheduler qos-priority-scheduler.old
unzip ~/Downloads/qos-priority-scheduler-v2.zip -d ~/projects
cd qos-priority-scheduler
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew test
./gradlew :app:assembleDebug -Pnetvalve.netstack=true
git add -A && git commit -m "Cleanup, scheduler core with tests and simulator, docs v2"
```

**Make a clean zip (never Finder):**

```bash
cd ~/projects
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
| 11 | **round-robin was unfair** (one flow got 8 MB, another 0.6 MB in the same tier) | a tiny floor grant used up a flow's turn | fixed in `PriorityPacerCore.serve`; found by the new unit tests |

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

## 10. Results so far (simulation, not the phone)

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

**Verification status.** Verified in a sandbox with Kotlin 2.0.21, JUnit 4.13.2, JDK 21 and the coroutines jar shipped in the Kotlin distribution: all 36 tests pass and the scheduler sources compile with warnings as errors. **Not verified:** the Gradle and Android build of the whole app (Hilt, KSP, Compose), the 7 upstream suites under Gradle, Room, and anything on the phone. The first action after adopting this package is `./gradlew test` and an APK build (roadmap M0).

## 11. Remaining work (summary; details in the roadmap)

M0 adopt and verify · M1 PayProbe · M2 Go/No-Go experiment · M3 wire the scheduler into the relay · M4 tier UI · M5 in-app traffic simulator · M6 evaluation on the phone · M7 optional stretch (on-device calibration, Mode A on device, graph, automation) · M8 report, viva, demo. The fast path is M0, M1, M2, M3, M4, M5, M6, M8.

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
- Machine: MacBook Air M1, folder `~/projects/qos-priority-scheduler`, phone OnePlus Nord CE5 (OxygenOS 16).

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
