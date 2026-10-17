# How to run the experiment (M2 and M6) step by step

For the student and for the viva. Written in plain words. Every number that is only an expectation is marked
"expected (hypothesis)". The fixed plan, scenarios and success criteria are in `docs/IMPLEMENTATION_ROADMAP.md` (M6).

## 1. Names (use these, everywhere)

| In the app | In the report | What it means |
|---|---|---|
| **Priority Control** | priority pacer, "Mode B" | When apps compete, High apps are served first, then Medium, then Low. Low gets what is left. |
| **Shared speed** | rate R | The total speed the controlled apps share. Set to about 90% of the real internet speed. |
| **High / Medium / Low** | tiers | PayProbe is always High (fixed). NetValve itself is always Low (fixed). Other apps are Medium unless you change them. |
| **Load tab** | in-app traffic simulator | Makes heavy traffic as NetValve itself, so it is the "Low app". |
| **Home** tab | dashboard | Start protection, checklist, Priority Control, controlled apps, tunnel settings. |

Why "Priority Control": "scheduler" and "Mode B" mean nothing to a user. "Mode B" stays in the report to tell it apart
from the simulation-only Mode A.

## 2. One-time settings (do not change them during a series of runs)

On the phone, **Home** tab:

1. Tunnel settings: **Control only the apps I pick = ON**, **Pass IPv6 traffic through = ON**, **Keep name lookups (DNS) fast = ON**, **Start automatically after the phone restarts = OFF**.
2. **Apps** tab: tick **PayProbe** and **NetValve**. Leave every other app unticked. PayProbe shows "High (fixed)", NetValve shows "Low (fixed)".
3. Priority Control: **OFF** for now.
4. Network: the test phone joins **the other phone's hotspot WiFi** with its own **mobile data OFF**. The hotspot phone stays next to it, on a charger, doing nothing else. Do not use the college WiFi. (The 16 Oct smoke run used the test phone itself as the hotspot host; that is not this setup.)
5. Keep the phone in the same place for the whole series. Battery saver OFF.

## 3. Find the real speed (once per network, and again after a break)

1. Home: press **Start protection** (OK on the VPN question). Priority Control stays **OFF**.
2. **Load** tab: set auto-stop to **20**, press **Download**. Watch "Speed now" and wait for the run to end.
3. Write down **"Average this run ... Mbps down"**. That is your measured download speed.
4. Repeat with **Upload** (the upload address `speed.cloudflare.com/__up` is not verified; if it shows errors, skip upload and set only the download speed).
5. Home, Priority Control, "Your measured speed": type the two numbers, press **Use 90% as shared speed**. The line "Now: ..." shows the saved values.

The hotspot speed can change with signal. Measure again before every 3 to 4 runs. If the speed moved by more than about 20%, set the shared speed again.

## 4. One run (about 2 minutes)

Names for the run labels follow the roadmap: configuration + scenario + run number.

| Config | Priority Control | Other setting |
|---|---|---|
| **C1** | OFF | none (VPN on, no priority) |
| **C4** | ON, shared speed set | none |
| **C2** | OFF | per-app limit on NetValve of about one fifth of the shared speed (the sliders icon next to NetValve in the Apps tab) |
| **C0** | OFF, **Stop protection** | VPN off (overhead check only) |

Scenario **S1** (downlink contention) = the Load tab button **Download** (3 downloads) while PayProbe measures.
**S2** = **Upload** (2 uploads). **S3** = **Both**.

Steps for one run, example C1, S1, run 1:

1. Home: set Priority Control as the table says (OFF for C1, ON for C4). Protection is ON.
2. Load tab: auto-stop **60**. Do not press anything yet.
3. PayProbe: run name `C1_S1_r1`, run length **90**, press **Start**.
4. When PayProbe shows about **10 s**, switch to NetValve, Load tab, press **Download**.
5. Wait. The Load run ends by itself after 60 s and PayProbe after 90 s. Do not touch the phone.
6. Write on paper: run label, network, hotspot signal, time. Wait **30 s**, then the next run.

Order of runs: **alternate** the configurations so slow drift in the network hits both equally:
`C1_S1_r1, C4_S1_r1, C1_S1_r2, C4_S1_r2, ...` up to r5 each. Then C2 (5 runs), then S2 and S3 the same way.
Before the whole series, one idle run per network is useful: PayProbe only, 90 s, name `C1_idle_r1`.

Data use (estimate): one 60 s download load at 20 Mbps is about 150 MB. Ten runs are about 1.5 GB. Check the hotspot data pack first.

## 5. Look at the numbers (Mac)

```
mkdir -p ~/Desktop/payprobe_run && adb pull /sdcard/Android/data/dev.netvalve.payprobe/files/ ~/Desktop/payprobe_run
cd ~/Downloads/qos-priority-scheduler
python3 analysis/summarize.py ~/Desktop/payprobe_run --from-sec 15 --to-sec 65
```

`--from-sec 15 --to-sec 65` keeps only the part where the Load traffic was surely running (it starts about 10 s in and lasts 60 s).
The idle part (first 10 s) shows what PayProbe sees without load: `--from-sec 0 --to-sec 10`. Copy the CSVs you keep into
`docs/data/` in a new folder with the date (never delete runs; note junk files in the docs instead).

Success criterion 1 of the roadmap: C4 p95 at most 50% of C1 p95 in at least 4 of 5 runs. Report it as hit or missed.

## 6. What I expect (hypothesis, not a result)

- **C1 (no priority):** the Load downloads fill the link; the waiting line forms in front of the link (router, carrier), outside the phone,
  so PayProbe's p95 should go up compared with idle.
- **C4 (Priority Control ON, shared speed about 90% of the link):** the waiting line forms inside the phone, where PayProbe (High) goes first,
  so PayProbe's p95 should stay close to idle while the Low downloads still use most of the link.
- The size of the effect is unknown until measured. Compare the phone with the simulation results in handoff Section 10; the phone may show less than the simulation.

Things that can make the effect smaller or invisible (check them if the result is flat):
1. Shared speed too high compared with the real speed at that moment (the hotspot speed moved): measure again, set again.
2. Data already buffered inside the relay or the socket buffers before the pacer can reorder it (the "smaller upstream socket buffers" idea in the roadmap is not done).
3. Radio and hotspot variation between runs: that is why runs alternate and repeat 5 times.
4. Upload scenarios depend on the upload address working.

## 7. If something looks wrong

| You see | Do this |
|---|---|
| Home checklist shows "Set the shared speed" | Enter the measured speeds and press "Use 90% as shared speed". |
| Priority Control ON but PayProbe p95 is the same as C1 | Check the checklist is all green; check NetValve is ticked in Apps; measure the speed again. |
| PayProbe shows failures or 5000 ms values at the start | Wait until Protection shows ON for a few seconds before pressing Start (hypothesis from the early 3-row runs, handoff problem 13: a probe started while the tunnel is still coming up can time out). |
| Load tab shows errors | The address may be unreachable; try the Download button first and check the hotspot has internet. |
| Internet stops after installing a new APK | Stop the VPN before installing; if it is stuck, switch the VPN off in Android settings and start it again. |
