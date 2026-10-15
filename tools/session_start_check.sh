#!/usr/bin/env bash
# Start-of-session check for the qos-priority-scheduler project.
# Run from anywhere:  ./tools/session_start_check.sh        (quick, about 5 seconds)
#                     ./tools/session_start_check.sh --full (also runs tests and builds the APKs)
# It only READS and REPORTS. It never changes a file. Paste its output into the chat and
# write the differences into the "Entry check" of the new entry in docs/SESSION_LOG.md.

cd "$(dirname "$0")/.." || exit 1
FULL=0
[ "$1" = "--full" ] && FULL=1
OK=0
WARN=0
ok()   { printf '  [ OK ] %s\n' "$1"; OK=$((OK + 1)); }
warn() { printf '  [WARN] %s\n' "$1"; WARN=$((WARN + 1)); }
info() { printf '         %s\n' "$1"; }

echo "== 1. Repository"
BRANCH=$(git rev-parse --abbrev-ref HEAD 2>/dev/null)
if [ "$BRANCH" = "qos-scheduler" ]; then ok "branch is qos-scheduler"; else warn "branch is '$BRANCH' (expected qos-scheduler)"; fi
info "HEAD: $(git log -1 --format='%h %ad %s' --date=format:'%d %b %H:%M')"
DIRTY=$(git status --porcelain | wc -l | tr -d ' ')
if [ "$DIRTY" = "0" ]; then ok "working tree clean (everything committed)"; else
  warn "$DIRTY uncommitted change(s); last session did not end clean:"
  git status --short | head -10 | while read -r line; do info "$line"; done
fi
if git rev-parse --verify -q origin/qos-scheduler >/dev/null; then
  AHEAD=$(git rev-list --count origin/qos-scheduler..HEAD)
  if [ "$AHEAD" = "0" ]; then ok "GitHub branch is up to date"; else warn "$AHEAD commit(s) not pushed to GitHub"; fi
else
  warn "branch qos-scheduler is not on GitHub yet (git push origin qos-scheduler --tags)"
fi

echo "== 2. Last session's handoff versus now"
LAST=$(grep '^- HEAD at end:\|\*\*Handoff state:\*\* HEAD at end:' docs/SESSION_LOG.md 2>/dev/null | tail -1 | sed 's/.*HEAD at end: *//' | awk '{print $1}')
if [ -z "$LAST" ]; then warn "no 'HEAD at end' found in docs/SESSION_LOG.md"; else
  if git merge-base --is-ancestor "$LAST" HEAD 2>/dev/null; then ok "last logged commit $LAST is in the current history"; else warn "last logged commit $LAST is NOT in the current history"; fi
fi
for t in pinned-base week1-aar-built scheduler-core m1-payprobe-done; do
  if git rev-parse -q --verify "refs/tags/$t" >/dev/null; then ok "tag $t exists"; else warn "tag $t missing"; fi
done

echo "== 3. Key files"
for f in app/libs/netstack.aar payprobe/src/main/AndroidManifest.xml analysis/summarize.py \
         docs/PROJECT_HANDOFF.md docs/IMPLEMENTATION_ROADMAP.md docs/SESSION_LOG.md \
         app/src/main/kotlin/dev/netvalve/scheduler/PriorityPacerCore.kt; do
  if [ -f "$f" ]; then ok "$f"; else warn "missing: $f"; fi
done

echo "== 4. Milestone tracker (from the roadmap)"
grep '^| M[0-9]' docs/IMPLEMENTATION_ROADMAP.md | cut -c1-150 | while read -r line; do info "$line"; done
if grep -q 'FlowSupervisor' app/src/main/kotlin/dev/netvalve/network/FlowSupervisor.kt 2>/dev/null; then
  if grep -q 'SchedulerController\|SchedulerPacer\|sched?\.acquire\|pacer\.acquire' app/src/main/kotlin/dev/netvalve/network/FlowSupervisor.kt; then
    info "scheduler IS referenced in FlowSupervisor.kt (M3 code present; this does not prove it builds or works)"
  else
    info "scheduler is NOT referenced in FlowSupervisor.kt (M3 not started)"
  fi
fi

echo "== 5. Measurement data (docs/data, PayProbe CSVs)"
COUNT=0
HAS_C0=0
for f in $(find docs/data -name 'payprobe_*.csv' 2>/dev/null | sort); do
  COUNT=$((COUNT + 1))
  N=$(( $(wc -l < "$f") - 1 ))
  LABEL=$(sed -n 2p "$f" | cut -d, -f6)
  case "$LABEL" in C0*) HAS_C0=1 ;; esac
  if [ "$N" -lt 10 ]; then warn "tiny file ($N rows, label $LABEL): $f"; else info "$N rows  label=$LABEL  $(basename "$f")"; fi
done
if [ "$COUNT" = "0" ]; then warn "no PayProbe CSVs found"; fi
if [ "$HAS_C0" = "0" ]; then warn "no VPN-off (label starting C0) run in the data"; else ok "a C0 run exists"; fi

echo "== 6. Phone"
if command -v adb >/dev/null 2>&1; then
  DEV=$(adb devices 2>/dev/null | grep -c 'device$')
  if [ "$DEV" -ge 1 ]; then
    ok "phone connected"
    PK=$(adb shell pm list packages 2>/dev/null | grep -c 'netvalve')
    if [ "$PK" -ge 2 ]; then ok "NetValve and PayProbe installed"; else warn "found $PK netvalve package(s), expected 2"; fi
  else
    info "no phone connected (fine if this session needs no phone)"
  fi
else
  info "adb not found in PATH, phone check skipped"
fi

if [ "$FULL" = "1" ]; then
  echo "== 7. Full check (tests and builds, takes a few minutes)"
  if ./gradlew test -q; then ok "./gradlew test passed"; else warn "./gradlew test FAILED"; fi
  if ./gradlew :app:assembleDebug -Pnetvalve.netstack=true :payprobe:assembleDebug -q; then ok "both APKs build"; else warn "APK build FAILED"; fi
fi

echo "== 8. What the last session left open (from docs/SESSION_LOG.md)"
if [ -f docs/SESSION_LOG.md ]; then
  awk '/^## Session /{buf=""} {buf=buf $0 "\n"} END{printf "%s", buf}' docs/SESSION_LOG.md | sed -n '/Open at end/,$p' | while IFS= read -r line; do info "$line"; done
fi

echo
echo "Result: $OK ok, $WARN warning(s). Paste this output in the chat; each WARN goes into 'Entry check'."
exit 0
