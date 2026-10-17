# NOTICE

This project is a **fork of NetValve** (https://github.com/IEAmir/NetValve), licensed under the
Apache License 2.0 (see `LICENSE`). It was forked at commit `f7b34b0` (git tag `pinned-base`).

The original README and developer handoff are kept for reference in `docs/upstream/`.

## What we changed or added (modified upstream files are listed explicitly)

### Repairs to the upstream snapshot
| File | Change |
|---|---|
| `.gitignore` | removed the line `*.xml`, which had stopped the manifest and all resource XML from being committed; added `.kotlin/` and `__MACOSX/` |
| `app/src/main/AndroidManifest.xml` | **recreated** (missing upstream); declares permissions, activity, VPN service, boot receiver, `PACKAGE_USAGE_STATS` |
| `app/src/main/res/values/strings.xml`, `res/drawable/ic_stat_shield.xml` | **recreated** (missing upstream) from what the Kotlin code references |
| `netstack/build-aar.sh` | host compile check now targets `GOOS=linux GOARCH=arm64` so it works on macOS |
| `netstack/go.mod`, `netstack/go.sum` | updated by `go mod tidy` |
| `app/libs/netstack.aar` | built artifact, committed so nobody else needs Go |
| `gradlew`, `netstack/build-aar.sh` | executable bit restored |
| `gradle.properties` | memory and parallelism tuned for a laptop instead of a 4 GB CI box |

### Bug fixes and UI changes
| File | Change |
|---|---|
| `network/ConnectionManager.kt` | bind the TCP socket before `protect()` (hypothesis: `protect()` failed on a socket without a file descriptor); 18 Oct 2026: NAT64 fallback for IPv4 destinations that are unreachable (dials `64:ff9b::<IPv4>`), failed sockets are closed |
| `ui/navigation/Navigation.kt` | `switchTab` helper so the Dashboard tab works from every screen |
| `ui/apps/AppsScreen.kt`, `ui/apps/AppSelectionViewModel.kt` | real app icons, explanation of what a tick means, Tick all / Clear all |

### New code (our contribution, all under `app/src/main/kotlin/dev/netvalve/scheduler/`)
`SchedulerModel.kt`, `PriorityPacerCore.kt`, `AdaptiveThrottle.kt`, `CalibratorMath.kt`,
`SchedulerPacer.kt`, `TierAssignments.kt` (pure Kotlin, no Android imports), with unit tests and a
bottleneck simulator in `app/src/test/kotlin/dev/netvalve/scheduler/`.
Simulation output: `docs/data/`.

### New code in the relay package
`app/src/main/kotlin/dev/netvalve/network/Nat64.kt` (NAT64 address helpers and the IPv4 path memory, our own code) with `app/src/test/kotlin/dev/netvalve/network/Nat64Test.kt`.

### New module and tools
`payprobe/` (separate Gradle module and APK, our own code, no upstream code) and
`analysis/summarize.py`. The only upstream file touched for this is `settings.gradle.kts`
(one line: `include(":payprobe")`).

### Removed from the repository root
`README.fa.md` (Persian translation), `publish-to-github.sh` (upstream publishing helper),
`HANDOFF.md` and `README.md` (moved to `docs/upstream/`), duplicate `PROJECT_HANDOFF.md`.

Keep this file up to date whenever an upstream file is modified.
