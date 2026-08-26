#!/bin/sh
set -eu

REPO_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$REPO_ROOT"

if [ -z "${JAVA_HOME:-}" ]; then
  JAVA_HOME=$(/usr/libexec/java_home -v 21)
  export JAVA_HOME
fi

echo "[gate] branch"
BRANCH=$(git branch --show-current)
case "$BRANCH" in
  phase/*) ;;
  *) echo "Expected a phase/* branch, found: $BRANCH" >&2; exit 1 ;;
esac

echo "[gate] whitespace"
git diff --check

echo "[gate] secrets"
if git ls-files --error-unmatch .env.local >/dev/null 2>&1; then
  echo ".env.local must never be tracked" >&2
  exit 1
fi
if ! git check-ignore -q .env.local; then
  echo ".env.local must be ignored" >&2
  exit 1
fi

echo "[gate] compile"
./mvnw -DskipTests compile

echo "[gate] automated tests (excluding e2e)"
./mvnw test

echo "[gate] frontend contract, types, tests and production build"
if [ ! -d frontend/node_modules ]; then
  echo "frontend/node_modules is missing; run: cd frontend && npm ci" >&2
  exit 1
fi
(
  cd frontend
  npm run check:contracts
  npm run typecheck
  npm test
  npm run build
)

echo "[gate] PASS: common automated checks completed"
