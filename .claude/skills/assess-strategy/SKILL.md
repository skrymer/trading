---
name: assess-strategy
description: Run the full strategy-assessment battery (25y walk-forward spine, continuous backtest, Monte Carlo, deflated-Sharpe flag, regime decomposition vs the current regime) without short-circuiting, and produce one comprehensive report for human judgment — no verdict. Use to autopsy a firewall-dead candidate, to take a deployment read on a validated strategy given the current market regime, or to get the full picture of any config before deciding what to do with it. The firewall (/validate-candidate) remains the only road to TRADABLE.
argument-hint: "[candidate-name] [request.json path]"
---

# Strategy Assessment

The non-adjudicating funnel (ADR 0022): runs **everything**, reports **everything**, decides **nothing**.
Where `/validate-candidate` short-circuits and stamps a verdict, an assessment completes the whole
battery and hands the operator one report — including the regime view ("where did it earn, and what tape
are we in now"). The operator's recorded decision is the only terminal state. Strategy-neutral: substitute
the user's actual candidate/config everywhere.

## Workflow

1. **Pre-flight (Step 0)** — mechanical checks; blocks only on impossibility:
   ```bash
   python3 .claude/skills/assess-strategy/scripts/preflight.py \
     <request.json> /tmp/assess-conditions.json /tmp/assess-rankers.json \
     strategy_exploration/assessments/anchor-status.json
   ```
   - **Blockers** (exit 1): unknown condition/ranker types, span-disqualified signals, regime gates
     without an accepted anchor check (PASS or ACCEPT_WITH_LIMITATIONS — ADR 0024; only the gateable
     CRISIS/THRUST labels are legal, enforced downstream) → **stop, do not fire**
   - **Battery shaping**: Random ranker → multi-seed sweep; selecting ranker → Random-baseline arm
     (confirm entry permissiveness with the operator); missing `randomSeed` → pin one
   - **Advisories**: inline scripts (promotion/G14 reminder + lookahead smells), firewall-DEAD config
     (autopsy framing) → carried verbatim into the report
   Then open the ledger: `record_draft(...)` persists the request JSON (ADR 0017 discipline).

2. **Battery plan + ONE approval** — show every POST body + est. wall time; wait for explicit approval.
   One approval covers the cadence probe + the whole battery; amendments re-show the plan. Sequential
   runs only (engine OOMs on concurrency). The battery (full bodies in [REFERENCE.md](REFERENCE.md)):

   | # | Run | Est. | Purpose |
   |---|---|---|---|
   | 0 | Cadence probe (2 small backtests) | ~min | span + cadence sanity before the expensive spine |
   | 1 | 25y walk-forward spine (2000→2025) | ~40m | every gate for information, per-window table, SPY baseline |
   | 2 | Continuous 25y backtest | ~10–15m | the real un-stitched equity path; feeds the regime decomposition |
   | 3 | Monte Carlo on run 2 | ~min | path-risk envelope — **deviation from ADR 0022** (prescribes the stitched OOS trades; unimplementable until #161), see [REFERENCE.md](REFERENCE.md) |
   | 4 | Deflated-Sharpe flag | s | search-luck readout; `nEff` = dossier + assessment lineages |
   | 5 | *(shaped)* Random baseline / multi-seed | +~40m ea | only when Step 0 shaped them |

3. **Regime analysis** — no extra backtests: `GET /api/regime/decomposition/{run-2-backtestId}`,
   `GET /api/regime/sector-matrix`, `GET /api/regime/current`.

4. **The C annotation, then the analyst** — *before showing any results*, record the permanent
   `record_c_eyeballed(...)` annotation (the report shows full 2021–2025 numbers; from here the
   firewall's Block C verdict is decorative for this family). Spawn **assessment-analyst** with the
   `/tmp/assess-<candidate>-*` paths + the pre-flight report; it writes
   `strategy_exploration/assessments/<candidate>/assessment.md`.

5. **Record the decision** — one of `redesign` / `send-to-firewall` / `paper-trade` /
   `deploy-at-own-risk` / `shelve` + a one-line why, via `record_decision(...)`. The assessment's only
   terminal event.

## Critical boundaries

- **No verdict, ever.** TRADABLE/REJECTED never appear as assessment outcomes; gate rows read
  "pass/fail (informational)".
- **Rescue-forbidden stands (ADR 0023):** a successor that just gates out the regime the report showed
  losing is a disguised re-run — the lineage DISTINCT gate refuses it. The standing ARS warning prints
  under every regime table.
- **It never resurrects, settles, or kills.** A firewall-DEAD config may be assessed (autopsy), but no
  assessment outcome changes dossier state.
- **Three couplings only** to the statistical accounting: assessment runs count as firewall trials
  (deflated-Sharpe N), the operator-eyeballed-C annotation, the decision log.

Endpoints, ledger events, file layout, crash-resume, and the anchor-status contract:
see [REFERENCE.md](REFERENCE.md).
