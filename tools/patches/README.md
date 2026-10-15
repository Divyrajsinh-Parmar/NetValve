# Archived one-time patches (Sessions 7 to 9, 14 and 15 Oct 2026)

These files were used once, in this order, to change the docs and the code. They are kept for reference and
reproducibility. **Do not run them again on this repository:** every script checks its own markers and stops
with "already applied" or "run ... first"; the `.patch` files would fail `git apply --check`.

The whole chain was replayed in an AI sandbox copy of the 14 Oct 2026 project zip (commit `478f4cd`). It was not
replayed on the student's Mac. Scripts are run from the project root; `.patch` files with `git apply`.

| # | File | What it did | SHA-256 (first 16) |
|---|---|---|---|
| 1 | `session7_patch.py` | Session 7: audit written into handoff, roadmap and session log | `c197d048d0a90a83` |
| 2 | `session7b_patch.py` | Session 7: WiFi network of the idle runs is unknown; Termux; plan for repeating the baselines | `e607d30855101200` |
| 3 | `m3_m5_code.patch` | Session 8 (git apply): M3 wiring, M4 tier UI, M5 traffic simulator, tests, RuleEngine own-UID change | `bd982f5092f1e1e7` |
| 4 | `session8_patch.py` | Session 8: plan change, M3 to M5 status, problem 15 | `0ec95f8b97abb364` |
| 5 | `add_remaining_work.py` | Session 8: puts REMAINING_WORK.md into docs/ (needs REMAINING_WORK.md next to it) | `03ec2e52ebf017f0` |
| 6 | `REMAINING_WORK.md` | Session 8: the file that add_remaining_work.py copies into docs/ | `8aaedb5be45f4f80` |
| 7 | `payprobe_test_fix.patch` | Session 9 (git apply): PayProbeCoreTest server on ServerSocket | `25fae365da19925c` |
| 8 | `session9_patch.py` | Session 9: first full Gradle check, problem 16 | `56a12b1f6002f042` |
| 9 | `prober_timeout_fix.patch` | Session 9 (git apply): every timeout stored as "timeout"; new test | `4db2a3a1d09352e6` |
| 10 | `session9b_patch.py` | Session 9: docs for the Prober fix | `91c989c1607b99dc` |
| 11 | `simulator_stop_fix.patch` | Session 9 (git apply): simulator stop race (toList on a concurrent set) | `225806642801f86a` |
| 12 | `session9c_patch.py` | Session 9: docs for the simulator fix, problem 17 | `a37bbcf5ecfc0789` |
| 13 | `session9d_patch.py` | Session 9: docs after ./gradlew test --continue was BUILD SUCCESSFUL | `bf5ac15b6dba99ee` |
| 14 | `session9e_patch.py` | Session 9: status update inside the Session 7 entry | `b4e7c93727c44f7c` |

`archive_patches.py`, the script that created this folder, is not archived here; it is only needed once.
