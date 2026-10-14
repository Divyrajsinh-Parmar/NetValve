#!/usr/bin/env bash
# Save everything: commit and push to GitHub, but only if the docs were updated too.
#   ./tools/finish_session.sh "commit message"             normal use
#   ./tools/finish_session.sh "commit message" --no-docs   only when no docs change is needed
cd "$(dirname "$0")/.." || exit 1
MSG="$1"
if [ -z "$MSG" ]; then
  echo 'Usage: ./tools/finish_session.sh "commit message" [--no-docs]'
  exit 1
fi
BRANCH=$(git rev-parse --abbrev-ref HEAD)
if [ "$BRANCH" != "qos-scheduler" ]; then
  echo "STOP: you are on branch '$BRANCH', expected qos-scheduler."
  exit 1
fi

git add -A
if git diff --cached --quiet; then
  echo "Nothing to commit. Everything is already saved."
  exit 0
fi
echo "Files in this commit:"
git diff --cached --stat | tail -15

if [ "$2" != "--no-docs" ]; then
  CHANGED=$(git diff --cached --name-only)
  if ! echo "$CHANGED" | grep -q '^docs/SESSION_LOG.md$'; then
    echo
    echo "STOP: docs/SESSION_LOG.md is not part of this change. Add the session entry first."
    git reset -q
    exit 1
  fi
  if echo "$CHANGED" | grep -q -E '^(app/src|payprobe/src|analysis/|netstack/)' && ! echo "$CHANGED" | grep -q '^docs/PROJECT_HANDOFF.md$'; then
    echo
    echo "STOP: code changed but docs/PROJECT_HANDOFF.md did not. Update the docs first."
    git reset -q
    exit 1
  fi
fi

git commit -q -m "$MSG" || exit 1
echo "Committed: $(git log -1 --format='%h %s')"
if git push origin qos-scheduler --tags; then
  echo "Pushed to GitHub."
else
  echo "WARNING: push failed. The commit is saved locally; run: git push origin qos-scheduler --tags"
  exit 1
fi
echo
./tools/session_start_check.sh | grep -E 'WARN|Result'
