#!/usr/bin/env bash
# Fails if a Google API key, keystore or local.properties is tracked or staged.
# Run before every push:  ./scripts/check-secrets.sh
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
fail=0
files=$( { git ls-files; git diff --cached --name-only; } | sort -u)
if echo "$files" | grep -E -i '(^|/)(local\.properties|secrets\.properties|keystore\.properties)$|\.(jks|keystore|p12|pem)$'; then
  echo "ERROR: secret-bearing file is tracked or staged (see above)." >&2
  fail=1
fi
if git grep -n -I -E 'AIza[0-9A-Za-z_-]{30,}' -- . ':!scripts/check-secrets.sh' ; then
  echo "ERROR: something that looks like a Google API key is in the tree." >&2
  fail=1
fi
if git diff --cached -U0 | grep -E '^\+.*AIza[0-9A-Za-z_-]{30,}'; then
  echo "ERROR: a staged change adds something that looks like a Google API key." >&2
  fail=1
fi
[ "$fail" -eq 0 ] && echo "check-secrets: OK"
exit "$fail"
