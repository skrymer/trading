# Strategy Assessment — Reference

## Environment

Run against PRD (`http://localhost:9080/udgaard`, `X-API-Key` header — POSTs via
`.claude/scripts/udgaard-post.sh`) unless the operator directs otherwise; dev (`:8080`) is fine when it
holds the full re-ingested universe. One run at a time.

## Discovery + config hash (pre-flight inputs)

```bash
curl -s -H "X-API-Key: $API_KEY" $BASE/api/backtest/conditions > /tmp/assess-conditions.json
curl -s -H "X-API-Key: $API_KEY" $BASE/api/backtest/rankers   > /tmp/assess-rankers.json
python3 -c "
import sys, json
sys.path.insert(0, '.claude/skills/strategy-exploration/scripts')
import config_hash
print(config_hash.config_hash(json.load(open('<request.json>'))))"
```

Dead-config check (autopsy framing, never refusal): collect DEAD hashes from
`strategy_exploration/dossier/*.jsonl` RECORD events and pass them to `preflight.check` together with
the candidate's hash.

## The battery (full request bodies)

**0 — Cadence probe** (×2, early + late window, ~300-symbol sanity universe):

```bash
.claude/scripts/udgaard-post.sh /api/backtest '{
  ...candidate request..., "symbols": [<sanity-universe>],
  "startDate": "2003-01-01", "endDate": "2004-12-31"
}' /tmp/assess-<candidate>-probe-early.json
# repeat with 2023-01-01 → 2024-12-31 → probe-late.json
```

Interpretation: ~0 trades in both windows = never-fires (stop, config problem); trades only in the late
window = span problem the pre-flight missed (stop); very high cadence = OOM risk on the full universe
(warn, consider the sanity universe for the spine).

**1 — 25y walk-forward spine** (the firewall's 25y-aggregate cadence):

```bash
.claude/scripts/udgaard-post.sh /api/backtest/walk-forward '{
  ...candidate request...,
  "startDate": "2000-01-01", "endDate": "2025-12-31",
  "inSampleMonths": 36, "outOfSampleMonths": 12, "stepMonths": 12
}' /tmp/assess-<candidate>-spine.json
```

**2 — Continuous 25y backtest** (same config, full span, position-sized):

```bash
.claude/scripts/udgaard-post.sh /api/backtest '{
  ...candidate request..., "startDate": "2000-01-01", "endDate": "2025-12-31"
}' /tmp/assess-<candidate>-continuous.json   # note its backtestId
```

**3 — Monte Carlo** on the continuous run's trades (`POST /api/monte-carlo/simulate`, per the
`/monte-carlo` skill's request shape).

> **⚠ Documented deviation (issue #161):** ADR 0022 prescribes Monte Carlo on the **stitched OOS
> trades** (the spine), but the MC endpoint only consumes a stored single-backtest `backtestId` and
> walk-forward runs are never stored — so the battery substitutes the continuous run's trades. That
> population is IS-inclusive: larger and statistically more flattering than the prescription. The
> report MUST label the MC section "computed on the continuous run's trades (IS-inclusive) —
> deviation from ADR 0022 pending #161". Remove this stamp when #161 lands.

**4 — Deflated-Sharpe flag** (`POST /api/risk/deflated-sharpe`): `observedSharpe` from the spine's
`aggregateOosRiskMetrics.sharpeRatio` (de-annualized by √252 — the PSR/DSR formulae are
per-observation; see `strategy-exploration/scripts/dsr_flag.py`), `nEff` and
`trialSharpeVariance` assembled from the union of:

```python
sys.path.insert(0, '.claude/skills/strategy-exploration/scripts')
sys.path.insert(0, '.claude/skills/assess-strategy/scripts')
import registry, ledger
trials = registry.collect_firewall_trials('strategy_exploration/dossier') \
       + ledger.collect_assessment_trials('strategy_exploration/assessments')
```

The itemized lineage list (dossier + assessment lineages) is always published with the flag.

**5 — Shaped arms** (only when Step 0 shaped them): the Random-baseline arm is the byte-identical spine
request with `ranker → Random` and a swept seed (per the random-ranker-baseline rule); the multi-seed
sweep re-fires the spine across ≥5 seeds and reports the cloud, not a single draw.

## Regime analysis

```bash
curl -s -H "X-API-Key: $API_KEY" "$BASE/api/regime/decomposition/<run-2-backtestId>" > /tmp/assess-<candidate>-regime.json
curl -s -H "X-API-Key: $API_KEY" "$BASE/api/regime/sector-matrix?after=2000-01-01&before=<today>" > /tmp/assess-<candidate>-sector-matrix.json
curl -s -H "X-API-Key: $API_KEY" "$BASE/api/regime/current" > /tmp/assess-<candidate>-current-regime.json
```

The decomposition buckets the **continuous** run's trades by the published label at entry (what an
operator consulting the read-out live would have seen) with insufficient-N floors the report respects
verbatim. State in the report that it covers the continuous run's trades, not the stitched-OOS subset.

## Ledger + file layout

```
strategy_exploration/assessments/
  anchor-status.json                  # {"verdict": "PASS"|"ACCEPT_WITH_LIMITATIONS"|"FAIL", "date": …} —
                                      # recorded after reference_check/regime_readout_anchor_check.py;
                                      # the pre-flight's regime-gate prerequisite reads it (PASS or
                                      # ACCEPT_WITH_LIMITATIONS clears the gate; ADR 0024)
  <candidate>/
    <candidate>.request.json          # the exact validated request (ADR 0017)
    ledger.jsonl                      # append-only events
    assessment.md                     # the analyst's report
```

Event vocabulary (`scripts/ledger.py`): `DRAFT` (opens; persists the request), `PREFLIGHT` (the report),
`FIRED` / `RUN_RECORDED` (one pair per battery run — crash-resume: a `FIRED` with no `RUN_RECORDED`
means check for the saved result before re-firing), `C_EYEBALLED` (permanent), `DECISION` (terminal;
vocabulary enforced by `record_decision`).

## Interpretation guardrails (the analyst owns these; the skill enforces their presence)

- **A/B/C-range slices of the spine are proxies** for the firewall's block verdicts (different
  IS-anchoring and window phasing) — labeled as such, with real firewall JSONs shown beside them when
  they exist. Never re-fire the firewall blocks for a report.
- Every 2021–2025 section carries the contamination stamp (operator-eyeballed-C, ADR 0022).
- The regime table is descriptive sizing/timing context for the operator — using it to select or
  redesign (e.g. "add a grind gate") is Aliased Regime Sensitivity; the standing warning prints under
  every regime table.
