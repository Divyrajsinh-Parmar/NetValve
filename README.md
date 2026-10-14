# Priority-Aware Traffic Scheduler for Android (root-free)

College project (Computer Networks). A fork of [NetValve](https://github.com/IEAmir/NetValve) that adds a
**work-conserving priority scheduler** to a local-VPN relay: apps in a High tier get their packets out
first when apps compete for the same link, and Low apps still use the whole link when nobody important
is active. No root, no server.

> Honest positioning: it runs **locally with no server**. It does not encrypt, hide your IP, or make you
> "private". Only one VPN can run at a time on Android, so it cannot run next to another VPN app.

## Where to start

| Read | For |
|---|---|
| `docs/PROJECT_HANDOFF.md` | full history, current status, decisions, results so far |
| `docs/IMPLEMENTATION_ROADMAP.md` | what is left, in order, with exact files to touch |
| `NOTICE.md` | what is upstream and what we changed |
| `docs/data/sim_results_table.txt` | algorithm-level results (simulation) |
| `docs/upstream/` | the original NetValve README and developer handoff |

## Build (macOS, Apple Silicon)

```bash
echo "sdk.dir=$ANDROID_HOME" > local.properties          # once
./gradlew test                                              # JVM unit tests, includes the scheduler
./gradlew :app:assembleDebug -Pnetvalve.netstack=true       # real engine (needs app/libs/netstack.aar)
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`app/libs/netstack.aar` is committed. To rebuild it: `cd netstack && ./build-aar.sh`
(needs Go and NDK 27.x; see `docs/PROJECT_HANDOFF.md`, Section 5).

## Status

Setup, repairs, the verified scheduler core and the PayProbe measuring app (`payprobe/`, verified on the phone)
exist; the first idle latency baselines are recorded and being repeated. The scheduler is **not yet wired into the relay**. See the roadmap.

PayProbe: `./gradlew :payprobe:assembleDebug`, then `adb install -r payprobe/build/outputs/apk/debug/payprobe-debug.apk`.
Summarise its CSVs with `python3 analysis/summarize.py <folder>`.

License: Apache-2.0 (inherited from NetValve).
