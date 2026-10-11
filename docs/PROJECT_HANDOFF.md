# NetValve-Based Priority-Aware Traffic Scheduler for Android

**Project Handoff and Progress Report** · Version 1.0 · 7 Oct 2026 Author: Divyrajsinh Parmar (Computer Engineering, IITRAM Ahmedabad) · Course: Computer Networks (college project)

## 0. How to use this document

Give this file to a new AI chat together with this message: *Read this report fully. It is my project history, current state and roadmap. Continue from Section 12 (Next steps). Follow Section 15 on how to talk to me.*

**Status at time of writing:** Week 1 of 8 (environment setup). Gate 1 (real traffic forwarding plus Go/No-Go experiment) is **not yet passed**. The app builds with the real network engine and installs on the phone, and a first fix round (Section 9) was applied but not yet built or tested.

## 1. Executive summary

Stock Android gives users no way to say which app should win when several apps compete for the same network link (for example a cloud backup running during a video call or an online exam). This project adds a **priority-aware traffic scheduler** to an existing open-source local-VPN traffic shaper (**NetValve**, Apache-2.0). It needs no root and no server.

The idea: put apps into High, Medium and Low tiers. A work-conserving scheduler inside the VPN relay lets Low apps use the whole link when nothing important is happening, and makes them yield immediately when a High app needs the link. Success is measured by the latency of a small probe app (PayProbe) while a simulator app generates heavy load.

**What is already done:** development machine set up, NetValve forked and pinned, repository bugs fixed, native network engine (netstack AAR) built, APK built and installed on a physical OnePlus Nord CE5, first smoke test run.

**What is next:** verify the first fix round, run the Week-1 Go/No-Go latency experiment, then follow the 8-week roadmap (Section 11).

## 2. Project aim and feasibility

| Item | Detail |
| --- | --- |
| Problem | No user-facing control over which app gets bandwidth during contention on stock Android |
| Goal | Per-app priority tiers (High, Medium, Low, Bypass) with a work-conserving scheduler in a local VPN relay |
| Method | Fork NetValve, add a priority pacer next to its token-bucket throttle, measure with PayProbe |
| Budget | Zero (open-source only) |
| Timeline | 8 weeks, with 3 go/no-go gates |
| Platform | Android 10+ (API 29+), physical arm64 phone, no root |
| Language | Kotlin (Go only to build the network-stack library) |

**Feasibility verdict (from the roadmap):** Android does not block this design. VpnService is an official API for intercepting and relaying other apps' traffic, and getConnectionOwnerUid (API 29+) maps connections to apps. The risks are engineering and measurement risks, not permission risks.

**What will not work (stated up front):** changing the kernel queueing discipline (needs root), making a router or cell tower favour our packets, controlling traffic that never enters the tunnel (excluded apps, tethered clients, VoLTE/IMS), prioritising banking/UPI apps that refuse to run with a VPN.

**Still unproven until measured:** that forwarding is reliable on this phone, that the high app's latency improves by a useful margin on the real network, and UDP/QUIC attribution on this kernel.

## 3. Core theory (one paragraph)

Latency = propagation + transmission + queueing. When total load exceeds link capacity, a queue builds at the bottleneck (router, access point, cell scheduler) and every app's packets wait in the same FIFO (bufferbloat). If the phone shapes its own traffic to a rate R slightly below real capacity (about 0.9 of measured goodput, as SQM/CAKE routers do), the queue forms inside our pacer instead, where we choose the order: High first. A static cap on Low wastes bandwidth when High is idle; a work-conserving priority scheduler does not.

## 4. Base project and source of all code

| Item | Value |
| --- | --- |
| Upstream repository | https://github.com/IEAmir/NetValve (Apache-2.0) |
| Our fork | https://github.com/Divyrajsinh-Parmar/NetValve |
| Local folder | \~/projects/qos-priority-scheduler (Desktop shortcut exists) |
| Pinned base commit | **f7b34b0** (Update README.md), git tag **pinned-base** |
| Working branch | qos-scheduler |
| Original zip | NetValve-main.zip (kept only as a reference copy; not used for work) |

**What NetValve provides:** per-app shaping through a local VPN, gVisor netstack (a userspace TCP/IP stack, built with gomobile into an AAR), token-bucket pacing in both directions, UDP pacing, DNS exemption, UID attribution, a plugin API, Jetpack Compose UI, Room and DataStore.

**Key code locations (all under app/src/main/kotlin/dev/netvalve/):**

| Path | Role |
| --- | --- |
| service/NetValveVpnService.kt | The VpnService; builds the TUN, applies the allowed/disallowed app list, acts as the socket protector |
| network/FlowSupervisor.kt | Relays each TCP/UDP flow; **this is the hook point for our scheduler** |
| network/ConnectionManager.kt | Opens protected upstream sockets to the real internet |
| throttle/ | Token buckets and pacing queue (ThrottleManager.bucketFor and pace) |
| service/TrafficEngine.kt, DeviceStateMonitorImpl.kt | Engine wiring; foreground-app detection via UsageStatsManager |
| ui/ (apps, dashboard, logs, stats, navigation) | Compose screens |
| src/netstack/kotlin vs src/loopback/kotlin | Engine implementation, switched by a Gradle flag |
| netstack/ (Go), netstack/build-aar.sh | Builds app/libs/netstack.aar |

**Hook point (identified):** in FlowSupervisor.relayTcp, bytes are paced by throttleManager.pace(bucket, n) just before each write to the upstream socket (upload) and before each write to the app side (download). The priority pacer will be placed beside this call. Docs to read for detail: docs/ARCHITECTURE.md, docs/EXTENDING.md, docs/THROTTLING.md.

**Engine flag:** the default build uses a loopback engine that does NOT forward traffic. The real engine needs the AAR and the Gradle property -Pnetvalve.netstack=true (arm64 only).

## 5. Development environment (as installed)

**Machine:** MacBook Air M1 (macOS, Apple Silicon). **Test phone:** OnePlus Nord CE5 running OxygenOS 16 (build 16.0.5.1002).

| Tool | Version / detail | Why |
| --- | --- | --- |
| Xcode Command Line Tools | installed | git and compilers (full Xcode is not needed) |
| Homebrew | 6.0.20 | installs developer tools |
| JDK | Temurin 17.0.20.1 (JDK 17 on purpose) | Gradle and Android build; project verified on 17 |
| Go | 1.27.1 (go.mod needs 1.25+), GOTOOLCHAIN=auto | builds the netstack AAR |
| gomobile and gobind | installed by build-aar.sh | binds Go to Android (AAR) |
| Android Studio | Apple-silicon build | SDK manager and IDE |
| Android SDK Platform | 35 (plain Android 15, no ext variants) | project compileSdk is 35 |
| Build-Tools | 35.0.0 (36.0.0 and 37.0.0 also present) | matches project |
| NDK | **27.1.12297006** (27.2 and 30.x also present; 27.x is the verified one) | required by gomobile |
| Platform-Tools (adb) | 37.0.1 | phone install and logs |
| Gradle | 8.10.2 (downloaded by ./gradlew) | build |

Environment variables in \~/.zshrc:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export ANDROID_HOME=$HOME/Library/Android/sdk
export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/27.1.12297006
export PATH=$PATH:$ANDROID_HOME/platform-tools:$(go env GOPATH)/bin
export GOTOOLCHAIN=auto
```

The project also needs a local file local.properties containing: sdk.dir=/Users/divyrajsinh/Library/Android/sdk

**Note:** the repo's gradle.properties is tuned for a 4 GB machine (single JVM, no daemon, no parallel, no incremental compile), so builds are slower than they need to be. Optional tweak on this Mac: raise -Xmx and set org.gradle.parallel=true.

## 6. Repository state and git history

| Commit or tag | Meaning |
| --- | --- |
| f7b34b0 / tag pinned-base | Upstream state we forked from |
| a482a91 | Restored missing AndroidManifest.xml and resources; fixed .gitignore; made gradlew and build-aar.sh executable |
| ea5f537 / tag week1-aar-built | Added app/libs/netstack.aar (3.9 MB), fixed build-aar.sh for macOS, updated go.mod and go.sum |
| Uncommitted (applied, not yet built) | First fix round: 5 files, see Section 9 |

After the fix round builds and is tested, commit it (suggested message: Fix usage-access permission, protect(), navigation, app icons) and tag the end of the week.

## 7. Setup and build log (what was done, in order)

1. Installed command line tools, Homebrew, JDK 17, Go and Git; verified versions.
2. Installed Android Studio; in SDK Manager selected only Android 15 (API 35) plus Build-Tools 35.0.0, NDK 27.x and Platform-Tools.
3. Set environment variables (Section 5).
4. Forked IEAmir/NetValve, cloned to \~/projects/qos-priority-scheduler, created branch qos-scheduler and tag pinned-base.
5. Wrote local.properties; made gradlew executable (chmod +x); first Gradle build failed because AndroidManifest.xml was missing (Section 8, issue 2); restored it; build succeeded (loopback engine).
6. Ran netstack/build-aar.sh; fixed its macOS host check (issue 4); AAR produced.
7. Built the real-engine APK: ./gradlew :app:assembleDebug -Pnetvalve.netstack=true (BUILD SUCCESSFUL; libgojni.so packaged).
8. Authorized the OnePlus Nord CE5 for adb (issue 5), installed the APK, ran the first smoke test.

Handy commands:

```bash
cd ~/projects/qos-priority-scheduler
./gradlew :app:assembleDebug -Pnetvalve.netstack=true
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -d | grep -i netvalve > ~/Desktop/netvalve-log.txt
```

## 8. Problems found and how they were solved

| # | Problem | Cause | Resolution |
| --- | --- | --- | --- |
| 1 | zsh: permission denied: ./gradlew | Executable bit lost in the upload | chmod +x gradlew netstack/build-aar.sh (committed) |
| 2 | Build failed: AndroidManifest.xml does not exist | Upstream .gitignore contains the line \*.xml, so the manifest and ALL resource XML files were never uploaded | Removed that .gitignore line; recreated AndroidManifest.xml, res/values/strings.xml and res/drawable/ic\_stat\_shield.xml from what the Kotlin code expects (a documented deviation from upstream, record it in NOTICE.md) |
| 3 | SDK Manager offered newer Build-Tools and NDK | Android Studio defaults to latest | Installed the verified versions (Build-Tools 35.0.0, NDK 27.1) and pointed ANDROID\_NDK\_HOME at 27.1 |
| 4 | build-aar.sh failed: gvisor rawfile build constraints exclude all Go files | The script's host compile check ran for macOS; gVisor packages are Linux/Android only | Changed the check to GOOS=linux GOARCH=arm64 CGO\_ENABLED=0 (committed) |
| 5 | adb devices showed unauthorized | Phone had not trusted the Mac | Revoke USB debugging authorizations, toggle USB debugging, unplug and replug with screen unlocked, accept the Allow USB debugging popup; if needed use Wireless debugging as plan B |
| 6 | Gradle build very slow | gradle.properties tuned for 4 GB CI | Be patient or raise memory and enable parallel (optional) |

## 9. Changes made on top of upstream (for NOTICE.md)

**Committed:** .gitignore (removed \*.xml); gradlew and netstack/build-aar.sh (executable, host check fixed); netstack/go.mod and go.sum (tidy); new AndroidManifest.xml and two resource files; app/libs/netstack.aar (built artifact, force-added).

**First fix round (applied locally, build and test pending)** after the first smoke test showed four problems:

| Observed on the phone | Cause | Change |
| --- | --- | --- |
| Usage access cannot be granted; only two unrelated system services appear in the list | Our recreated manifest omitted the PACKAGE\_USAGE\_STATS permission, so Android did not list NetValve | Added the permission to AndroidManifest.xml (certain) |
| Logs show protect() failed for TCP flows | **Hypothesis, unverified:** java.net.Socket creates its OS socket lazily, so protect() has no valid file descriptor | ConnectionManager.connectTcp now binds the socket before calling protect() |
| Dashboard tab does not respond after visiting Apps or its Manage apps button | Bottom-bar navigation back to the start destination did not work in that flow | Navigation.kt: new switchTab helper (pop back to Dashboard); Apps tab also stays highlighted on app detail |
| No app icons; selection checkbox unclear | Icons were never loaded (letter avatar only); meaning of the tick depends on selection mode | AppsScreen.kt shows real icons, an explanatory line, and Tick all / Clear all; AppSelectionViewModel.kt adds selectionMode and setAllShown, and sets pendingRestart before saving |

The five files: AndroidManifest.xml, ConnectionManager.kt, AppSelectionViewModel.kt, AppsScreen.kt, Navigation.kt. These changes were checked for patch consistency only; they have not been compiled yet.

## 10. Current status and test results

| Check | Result |
| --- | --- |
| Loopback build | Success |
| Real-engine build (netstack AAR) | Success (APK about 58 MB) |
| Install on OnePlus Nord CE5 | Success, no install error |
| First smoke test | Most features appear functional; usage access not granted (cause above); protect() failed log lines for TCP; Dashboard tab bug; no app icons |
| Real forwarding verified for Chrome, YouTube, WhatsApp | **Not yet confirmed** (needs re-test after the fix round) |
| Gate 1 | Not passed |

## 11. Roadmap (from the project plan)

**Scheduler design.** Tiers 0=High, 1=Medium, 2=Low; unattributed flows are treated as Medium; Bypass apps never enter the tunnel. Tick 10 ms, grant size 4 KB, separate pacer per direction. Rate R = 0.9 x measured goodput (calibration) or a fixed demo rate.

- **Mode OFF:** no scheduling (baseline).
- **Mode A, adaptive throttle:** reactive; throttle Low when High is active; 2 s hold prevents flapping.
- **Mode B, shared priority pacer (core contribution):** every tick, floors first (Medium 10 percent, Low 5 percent of the budget), then strict priority High then Medium then Low, round-robin across flows in a tier; while High is active Medium is capped at 50 percent and Low at 20 percent of R; when High is idle there are no caps (work-conserving). High tier holds at most 3 apps. UDP uses the same pacer with a bounded tail-drop queue; DNS stays exempt.
- Upstream sockets get modest buffers (about 64 KB) so queues do not hide in the kernel.

**New components to build:** scheduler package (SchedulerConfig, TierResolver, AdaptiveThrottle, PriorityPacer, Calibrator) with JVM unit tests and a discrete-event simulator; tier and Bypass UI with Room storage; Congestion Simulator (Low app); **PayProbe** companion app (separate APK and UID, High tier, 1 s HTTPS probes, CSV and broadcast); dashboard with latency graph and CSV export; experiment runner.

**Timeline**

| Week | Work | Gate |
| --- | --- | --- |
| 1 | Setup, real forwarding, hook point, Go/No-Go experiment | Gate 1 |
| 2 | Attribution checks (TCP/UDP share), PayProbe v0, baselines C0/C1, endpoint verification, Bypass test | Gate 2 |
| 3 | Scheduler core in pure Kotlin, JVM tests, simulator, Room and TierResolver | tests green |
| 4 | Tier/Bypass UI, Mode A on device | Mode A helps in S1 |
| 5 | Mode B both directions, calibration, socket tuning | Mode B works in S1 and S2 |
| 6 | Simulator app, dashboard, PayProbe broadcast, CSV export, runner; feature freeze | Gate 3 |
| 7 | Full evaluation protocol, edge cases F1-F15, 1-hour stability, battery comparison | data collected |
| 8 | Analysis, report, README and NOTICE, demo rehearsal, backup video | submission ready |

**Week-1 Go/No-Go experiment (no new code):** run 3-4 parallel downloads in one app; in another app time a tiny HTTPS request every 500 ms for 60 s and record p50 and p95; then apply a manual cap of about 20 percent of link speed to the downloader and repeat. If p95 drops about 2x or better: GO. If barely changes: try uploads, a phone hotspot or fixed-rate mode. If forwarding is unstable or the AAR will not build after a focused effort: Plan C.

**Evaluation configurations:** C0 no VPN, C1 VPN with scheduling off, C2 static cap on Low, C3 Mode A, C4 Mode B, C5 Mode B without floors, C6 Low app paused (ideal bound). **Scenarios:** S1 downlink contention, S2 uplink, S3 both, S4 High idle (work conservation), S5 10-minute run (starvation check), S6 real app as heavy flow, S7 WiFi to mobile switch, S8 PayProbe bypassed. Each 60 s with at least 5 repeats on a network we control (own router or phone hotspot, not college WiFi).

**Success criteria (fixed before testing):** (1) in S1-S3, C4 p95 at most 50 percent of C1 p95 in at least 4 of 5 runs; (2) in S4, C4 Low goodput at least 85 percent of R and clearly above C2; (3) no scheduler-caused starvation in S5; (4) at least 95 percent of bytes attributed to a real UID; (5) one-hour run without crash.

**Fallbacks:** Plan B is Mode A only on device. Plan C (if forwarding is unreliable by Gate 2) is the algorithm plus discrete-event simulation plus partial prototype.

**Cut list (drop in order):** drag-between-sections, battery depth, runner automation, Quick Settings tile, Mode A (if Mode B works), scenarios S6-S8.

## 12. Next steps (start here)

1. Build and install the fixed code: ./gradlew :app:assembleDebug -Pnetvalve.netstack=true, then adb install -r.
2. On the phone: turn OFF usage access for the two unrelated system services; grant usage access to NetValve; check Dashboard tab, app icons, Tick all and Clear all; in Logs confirm protect() failed no longer appears.
3. If the log line persists, collect one full line plus adb logcat output and re-diagnose (the bind fix is a hypothesis).
4. Commit the fix round and tag the week.
5. Smoke test: Chrome, YouTube, WhatsApp through the tunnel with per-app throughput visible. Record network type and signal; phone details are in Section 5.
6. Run the Go/No-Go experiment (Section 11) and record p50 and p95 numbers. Decide GO, GO with fixed rate, or Plan C.
7. Read docs/ARCHITECTURE.md, EXTENDING.md and THROTTLING.md and confirm the hook point and whether our own app and PayProbe are inside the routed set.
8. Begin Week 2 tasks (attribution share, PayProbe v0, baselines).

## 13. Risks and limitations to remember

- Only one VPN at a time; starting another VPN revokes ours. Some apps (banking, UPI) dislike VPNs and are out of scope.
- We control only tunnelled traffic and only help when the congestion is caused by our own device.
- Wrong R removes the benefit; control costs about 10 percent peak throughput plus CPU and battery.
- No AQM (no CoDel-style early drop); TCP is controlled by back-pressure.
- UDP/QUIC attribution depends on kernel support; the base is young (single maintainer), hence the pinned fork.
- Android 16: after updating a VPN app while the VPN is active, internet can break; always stop the VPN before reinstalling the APK (our phone runs OxygenOS 16, so this applies to us).
- OnePlus quirks: adb authorization popups, and a Disable permission monitoring toggle may be needed if installs complain about permissions.
- Fixed-rate demo mode can look rigged; also show calibrated mode, the real-app scenario S6 and the paused bound C6.

## 14. Glossary

- **VpnService:** official Android API for a local VPN that sees and relays other apps' traffic.
- **Netstack (gVisor):** a TCP/IP stack in user space, used to terminate app connections inside the app.
- **AAR:** an Android library archive; netstack.aar is built from Go with gomobile.
- **Token bucket / pacing:** allows bytes at a set rate; the base uses it for per-app caps.
- **Work-conserving:** never leaves capacity idle if someone has data waiting.
- **Bufferbloat:** large queues at a bottleneck that add delay.
- **UID:** Android's per-app identity; priority is per UID, hence two APKs (main app and PayProbe).
- **Pinned commit:** a fixed upstream version so the base cannot change under us.

## 15. Instructions for the AI assistant reading this

- The student is a **beginner**. Give small, numbered, copy-paste steps, one stage at a time, and ask for the output before moving on.
- Explain concepts in **Hinglish**, told like a short story or analogy, then give exact commands.
- Verify claims against the actual code or docs; say clearly when something is a hypothesis or untested (for example the protect() diagnosis).
- Keep new logic in framework-free Kotlin with JVM unit tests; never rewrite the relay; list every modified upstream file in NOTICE.md.
- Machine: MacBook Air M1; project folder: \~/projects/qos-priority-scheduler; phone: OnePlus Nord CE5 (OxygenOS 16, build 16.0.5.1002).
- Ask for error text (the What went wrong section for Gradle) rather than guessing.

## 16. Scope question raised on 7 Oct 2026 (open, decision pending)

**Concern (from the student):** banking and UPI apps usually refuse to run while a VPN is on, so a user who needs them must switch the VPN off, which returns the phone to its normal state. The student therefore doubts that the problem exists and wants the hypothesis and assumptions re-examined.

**Assessment so far:**

- The banking case is a real weakness as a *motivating example*. It was already listed as out of scope (Sections 2 and 13), but it should not be used to justify the project in the report or the viva.
- It does not remove the underlying problem. Bandwidth contention (a backup or update starving a video call, online exam or game) exists with the VPN off. The VPN is only the mechanism that lets a normal app see and order other apps' traffic.
- The cost is real: the user must keep our VPN on to get control, and must pause it or mark the banking app as Bypass when needed. Whether a Bypass app actually avoids VPN detection on OxygenOS 16 is **untested** (roadmap Week-2 check).
- Whether the benefit is large enough to matter is exactly what the Week-1 Go/No-Go experiment measures. Until that result exists, the hypothesis is neither proven nor killed.

**Options:** (A) keep the project and reframe the problem statement around latency-sensitive apps such as calls, exams, games and streaming, with PayProbe as a stand-in probe rather than a payment app; (B) keep it but make the Go/No-Go result the explicit deciding test; (C) pivot the topic. Recommended: A and B together. Record the final decision here.

**Scenario refinement (student, 7 Oct 2026):** the user is on a video call while also using a banking or UPI app, and the banking app must stay responsive. Two constraints shape the answer. Android allows only one VPN at a time, so a separate third-party VPN cannot run together with ours. A banking app that refuses a VPN has to be marked Bypass, which means it is **not prioritised directly** by our scheduler. The workable design is: the video call and background apps inside our tunnel, the banking app in Bypass, and the tunnel rate R kept below link capacity so the shared bottleneck queue stays short and the bypassed banking request is not stuck behind bulk data. This is scenario S8 and it is **unproven**. It also assumes the banking app does not detect the VPN when it is excluded (untested on OxygenOS 16). PayProbe should therefore be tested two ways: High tier inside the tunnel (S1-S3) and Bypass (S8).

**Privacy-VPN scenario (student, 7 Oct 2026):** professionals commonly keep a commercial privacy VPN (for example NordVPN) on all the time, and the student wants our tool to work in that situation. **As designed it cannot run at the same time as another VPN app.** Android allows one active VPN, and our scheduler is itself a VPN service, so starting ours revokes theirs (risk R11 in the roadmap). Our relay also sends traffic out through the real network, so it does not hide the user's IP the way a privacy VPN does; it only keeps data on the device (no server). Banking apps are usually handled in privacy VPNs by split tunnelling, which is the same idea as our Bypass list. Options: (A) accept and document this limit, and position the tool as a local alternative to a privacy VPN for people who want control rather than IP hiding (recommended for the 8-week scope); (B) future work: put the scheduler inside a VPN client that can carry the user's own tunnel (for example a WireGuard-based client), which is a large separate effort and depends on the provider allowing its own configuration to be used. Decision pending.

**Positioning rules (7 Oct 2026):** (1) Our VPN must be on for the scheduler to work. This is a design property, because VpnService is the only root-free way to see and order other apps' traffic, and it is stated as a limitation rather than hidden. (2) Do not claim that the app makes the user safe or private. It forwards bytes as they are: no encryption is added, no IP address is hidden and no TLS is intercepted. The honest claim is that it runs on the device with no server, so traffic is not handed to a third-party VPN company, and that the open-source base (Apache-2.0) lets the code be checked. Say local and server-free, not secure.

## 17. Change log

This section is updated after every project change.

| Date | Change | Sections touched |
| --- | --- | --- |
| 7 Oct 2026 | Report created from the setup session | all |
| 7 Oct 2026 | Phone recorded as OnePlus Nord CE5, OxygenOS 16 (build 16.0.5.1002); Android 16 VPN-update risk marked as applying | 5, 12, 13, 15 |
| 7 Oct 2026 | Scope question about banking and UPI apps recorded; decision pending | 16, 17 |
| 7 Oct 2026 | Scenario refined: video call plus banking app at once; banking app handled as Bypass; S8 becomes a key test | 16, 17 |
| 7 Oct 2026 | Privacy-VPN (NordVPN-style) scenario recorded: cannot coexist with our VPN; options A and B, decision pending | 16, 17 |
| 7 Oct 2026 | Positioning rules added: VPN must be on (stated limitation); no safe or private claims | 16, 17 |
| 7 Oct 2026 | Handoff package defined: this report saved as docs/PROJECT\_HANDOFF.md inside the repo, and the whole repo (with git history and the built AAR) zipped from the Mac | 0, 17 |
