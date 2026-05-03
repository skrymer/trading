#!/usr/bin/env bash
# POST a JSON body to the Udgaard PRD API and save the response.
#
# Usage:
#   .claude/scripts/udgaard-post.sh <endpoint-path> <json-body-or-@file> <output-file>
#
# Args:
#   endpoint-path   e.g. /api/backtest, /api/backtest/walk-forward, /api/monte-carlo/simulate
#   json-body       JSON string OR @path/to/file.json (curl @-prefix convention)
#   output-file     where the response is saved
#
# Env:
#   API_KEY         required — sent as X-API-Key header
#   UDGAARD_HOST    optional, defaults to http://localhost:9080/udgaard
#
# Behavior:
#   - Fails loudly on non-2xx HTTP status (response is still written so it can be inspected).
#   - Fails loudly on missing $API_KEY.
#   - Prints "OK: <bytes> bytes -> <output-file>" on success.

set -euo pipefail

if [[ $# -ne 3 ]]; then
  sed -n '2,21p' "$0" >&2
  exit 64
fi

if [[ -z "${API_KEY:-}" ]]; then
  echo "ERROR: API_KEY env var is unset" >&2
  exit 64
fi

endpoint="$1"
body="$2"
out="$3"
host="${UDGAARD_HOST:-http://localhost:9080/udgaard}"

http_status=$(curl -s -o "$out" -w '%{http_code}' \
  -X POST "${host}${endpoint}" \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d "$body")

if [[ ! "$http_status" =~ ^2 ]]; then
  echo "ERROR: HTTP $http_status from POST $endpoint" >&2
  echo "Response saved to $out for inspection:" >&2
  head -c 500 "$out" >&2
  echo >&2
  exit 1
fi

bytes=$(wc -c < "$out" | tr -d ' ')
echo "OK: ${bytes} bytes -> $out"
