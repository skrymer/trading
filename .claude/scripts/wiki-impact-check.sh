#!/usr/bin/env bash
# Detect changes to strategy-research METHODOLOGY surfaces that the knowledge/ wiki
# restates (firewall gates, funnel mechanics, failure-mode definitions), so a
# methodology change can't silently leave the analyst-consulted wiki stale.
#
# Usage: .claude/scripts/wiki-impact-check.sh
#
# Prints a human-readable report listing the changed methodology surfaces and the
# wiki concept pages most likely to reference them. Empty output = no
# methodology-relevant changes (the wiki cannot have drifted from this diff).
#
# This is a HEURISTIC. A path match does NOT mean the wiki is wrong — the reviewer
# reads the diff, extracts what actually changed (a threshold, a gate name, a term),
# greps knowledge/ for the OLD value, and reports stale hits. Wiki fixes go through
# /wiki-ingest (operator-curated research layer), never an ad-hoc pre-commit edit.

set -euo pipefail

changed=$({
  git diff --name-only HEAD
  git ls-files --others --exclude-standard
} | sort -u)

if [[ -z "$changed" ]]; then
  exit 0
fi

# Methodology surfaces the wiki's concepts/ + entities/ pages restate.
surfaces='^(docs/adr/|CONTEXT\.md$|\.claude/skills/(validate-candidate|strategy-screen|condition-screen|strategy-exploration)/|\.claude/agents/(firewall|strategy-screen|condition-screen|walk-forward|monte-carlo)-analyst\.md$)'

matches=$(echo "$changed" | grep -E "$surfaces" || true)

if [[ -z "$matches" ]]; then
  exit 0
fi

echo "Changed methodology surfaces (knowledge/ wiki may restate these):"
echo "$matches" | sed 's/^/  - /'
echo

# Was the wiki itself touched in the same diff? If not, it's a likely forgot-the-wiki.
if echo "$changed" | grep -qE '^knowledge/'; then
  echo "knowledge/ WAS modified in this diff — verify the RIGHT pages were updated"
  echo "(a methodology change often ripples to several concepts/ + entities/ pages)."
else
  echo "knowledge/ was NOT modified — likely stale if this diff changed a documented"
  echo "threshold, gate name, term, or failure-mode definition."
fi
echo
echo "Review: knowledge/wiki/concepts/ + entities/ for stale references to the"
echo "changed thresholds / gate names / terms. Grep the OLD values. Report drift as"
echo "WIKI DRIFT (report-only); apply fixes via /wiki-ingest, not an ad-hoc edit."
