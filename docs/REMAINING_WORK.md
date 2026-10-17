# Remaining work (state after Session 8, 14 Oct 2026)

Read this together with `docs/PROJECT_HANDOFF.md` (truth about the project), `docs/IMPLEMENTATION_ROADMAP.md` (plan) and `docs/SESSION_LOG.md` (what each session did). Only facts that were checked against a file, code or terminal output are stated as facts. Everything else is marked "not verified".

## 1. What exists and how much of it is verified

| Part | State | Evidence level |
|---|---|---|
| Scheduler algorithm (`PriorityPacerCore`, simulation) | built | 36 old JVM tests; passed again in an AI sandbox on 14 Oct 2026 with a stand-in test runner (not Gradle) |
| PayProbe app and analysis script | built, ran on the phone | CSVs in `docs/data/`; tables in handoff 10.1 and 10.2 recomputed and matching |
| M2 idle baselines | mobile data done; WiFi done but the network is unknown | to be repeated on the hotspot |
| M3 wiring into the relay, Dashboard card | code written, debug build passes | pure parts tested in the sandbox; `:app:assembleDebug` passed on the Mac (15 Oct); `./gradlew test --continue` BUILD SUCCESSFUL on the Mac (15 Oct); never run on the phone |
| M4 tier chips in the Apps screen | code written, debug build passes | same as M3 |
| M5 traffic simulator (Load tab) | code written | tested only against a local server on a JVM; Cloudflare `__up` not verified |
| M2 rest, M6, M7, M8 | not started | none |

## 2. Remaining work, in order

1. **Build and fix (Mac).** `./tools/session_start_check.sh --full` (runs `./gradlew test` and the debug build). Status 15 Oct 2026: both debug APKs build and `./gradlew test --continue` ended with BUILD SUCCESSFUL on the Mac, after the fixes for problems 16 and 17. Item 1 is done; the next item is 2, the phone smoke checks. Optional: Gradle warned that its daemon ran out of metaspace (`org.gradle.jvmargs` in `gradle.properties` has `-XX:MaxMetaspaceSize=1g`). Then commit with `./tools/finish_session.sh`.
2. **Phone smoke checks.** Stop the VPN, install the new APK, then: (a) scheduler OFF behaves like stock NetValve; (b) tier chips work and survive an app restart; a fourth High app is refused with the explanation; (c) tick NetValve in the Apps list (ONLY_SELECTED mode), restart the tunnel, press Download in the Load tab and see traffic; (d) the debug log shows the per-flow line with tier, mode and R.
3. **M2 on the hotspot network** (exact settings, taps, labels and analysis: `docs/HOW_TO_RUN_EXPERIMENT.md`). Second phone as hotspot, test phone on that WiFi with mobile data off. Record network name, band, signal and the hotspot phone's state. Repeat the idle baselines C1, C0, C1 (3 minutes each, always `--skip 10`). Measure link speed on the same network and set R = 0.9 x downlink. Do load runs (a) no control and (b) static cap, at least 5 repeats each. Write GO, GO with fixed R, or fallback in the handoff.
4. **M6 evaluation.** Configurations C0, C1, C2 (static cap), C4 (Mode B). Scenarios S1 to S5, S6, S8 (S7 only if time permits), 60 s each, at least 5 repeats, same place and time. Success criteria are fixed in the roadmap and must be reported honestly, hit or missed. Raw CSVs, summary table, graphs and one honest paragraph per criterion go in the repo.
5. **M7 stretch, only if time is left, in roadmap order:** calibration, Mode A on the device, PayProbe broadcast and live graph, experiment runner, edge cases and 1-hour run, Quick Settings tile.
6. **M8 report, viva and demo.** Final report, viva answers (question bank in the roadmap), a 6-minute demo with a recorded backup video and the CSVs.

Housekeeping, any time: decide what "old project removal" means for the GitHub fork (nothing was deleted so far; never touch `main`); review untouched docs (`ARCHITECTURE`, `LIMITATIONS`, `THROTTLING`, ...) for stale statements; check whether Cloudflare `__up` accepts 25 MB POSTs (otherwise change the upload URL); smaller upstream socket buffers (roadmap risk note) were not done; the simulator and load runs use a lot of data, so check the hotspot data pack first.

Known risk accepted by the student (14 Oct 2026): M3 to M5 were built before the M2 measurements. If the phone effect turns out small, that effort was spent before knowing. The fallback (simulation, tests, honest analysis) stays valid.

## 3. Which reports to update after each stage

| After | Update |
|---|---|
| every session | new entry in `docs/SESSION_LOG.md` (entry check, done with evidence, claimed not verified, handoff state, open items), then `./tools/finish_session.sh "message"` |
| build and fix | handoff Section 9.1 (replace "not built" with the real result), roadmap tracker rows M3 to M5, handoff Section 8 (problems found) |
| phone smoke checks | handoff Section 9.1 "Not verified yet" list, problem 15 (own UID) |
| M2 | handoff Section 10 (new tables with file names and labels), Section 11 (GO or No-Go), roadmap tracker M2 |
| M6 | handoff Section 10 (phone versus simulation), roadmap tracker M6, then the final report |
| any new data | raw CSVs in `docs/data/` with a label; never delete runs, record junk files instead |

## 4. Prompts to paste (copy the whole block)

**A. Start of any session**

> Project: priority scheduler on NetValve (repo ~/Downloads/qos-priority-scheduler, branch qos-scheduler, remote Divyrajsinh-Parmar/NetValve; never delete anything and never overwrite main). I attached the latest project zip. Before changing anything: read docs/PROJECT_HANDOFF.md (Section 13 says how to talk to me), docs/IMPLEMENTATION_ROADMAP.md, docs/SESSION_LOG.md and docs/REMAINING_WORK.md fully. Compare the last "Open at end" and "Next session, first steps" with the output of ./tools/session_start_check.sh that I paste below, match every WARN with the previous session, and mark anything written as done without a commit, file or output as "unverified". Then tell me the stage and what is left, and continue with the first unfinished item. Rules: Hinglish, short numbered steps, ask for my terminal output after each step, claim only what a file, code or output verifies (otherwise write "hypothesis"), test commands before giving them or say they are untested, give a docs patch plus a SESSION_LOG entry plus one ./tools/finish_session.sh command after each step. Ask questions with the tap-options tool.

**B. Build and fix (step 1)**

> I ran ./tools/session_start_check.sh --full. Here is the output and the compile errors: <paste>. Read the real files named in the errors before proposing a fix. Fix one cause at a time with an exact edit (patch or script), keep logic in framework-free Kotlin where possible, and tell me which command to run next. When both ./gradlew test and the debug build pass, give me the docs patch and the SESSION_LOG entry.

**C. Phone smoke checks (step 2)**

> Build passes. Walk me through the phone smoke checks in docs/REMAINING_WORK.md one at a time. For each check give the exact adb or phone steps, the output I should paste, and what a pass and a fail look like. Remember to stop the VPN before reinstalling on OxygenOS 16. Mark every check as verified only after I paste the evidence.

**D. M2 and M6 runs on the hotspot (steps 3 and 4)**

> Now M2 then M6 on the hotspot network. Read the roadmap sections for M2 and M6 and the success criteria first. Give me one run at a time: setup, the exact command or taps, the label to use for the CSV, where to save it, and the ./analysis/summarize.py command (always --skip 10). Record network name, band, signal and hotspot state with every batch. After each batch compute the numbers from the CSVs and say what is verified and what is hypothesis. Do not decide GO or No-Go before the data supports it.

**E. Final report (step 6)**

> Using only what is in docs/PROJECT_HANDOFF.md, docs/data/ and the SESSION_LOG, write the final report sections: problem, theory, design, simulation results, phone results, honest limits, and what is reused from NetValve versus built (NOTICE.md). Mark every number with its file or label. Do not claim anything that is in a "not verified" list. Then prepare the viva answers from the question bank in the roadmap and a 6-minute demo script.

**F. End of a session**

> This stage is finished. Give me the commit command (./tools/finish_session.sh "message"), the exact text to add to the change log, status table and results of docs/PROJECT_HANDOFF.md, the tracker row to update in docs/IMPLEMENTATION_ROADMAP.md, and the SESSION_LOG entry, with "Claimed, not verified" filled honestly.
