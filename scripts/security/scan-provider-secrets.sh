#!/usr/bin/env bash
set -euo pipefail

# Scan tracked repository text for high-confidence private/provider credentials.
# google-services.json is intentionally excluded: its Firebase API key is a
# public app identifier, not a provider credential. Restrict it in Google Cloud.
pattern='(-----BEGIN (RSA |EC )?PRIVATE KEY-----|((vak_live_|india_live_)[A-Za-z0-9_-]{20,})|(sk-[A-Za-z0-9]{20,})|(gh[pousr]_[A-Za-z0-9]{30,})|(xox[baprs]-[A-Za-z0-9-]{20,}))'

set +e
matches="$(git grep -nI -E "$pattern" -- . \
  ':!app/google-services.json' \
  ':!scripts/security/scan-provider-secrets.sh')"
status=$?
set -e

if [[ $status -gt 1 ]]; then
  echo "Secret scan could not inspect the Git working tree." >&2
  exit "$status"
fi

if [[ -n "$matches" ]]; then
  echo "Potential private/provider credential detected:" >&2
  echo "$matches" >&2
  exit 1
fi

echo "No high-confidence private/provider credential patterns found."
echo "Note: Firebase google-services.json is excluded because its API key is public app configuration; restrict its API and Android-app usage in Google Cloud."
