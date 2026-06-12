# The strategy assessment is a separate, non-adjudicating funnel that knowingly exposes the 2021–2025 OOS span

The validation funnel short-circuits at the first failing binding layer (a Block-A death reveals nothing about the rest of the tape) and its verdicts are deterministic — by 2026-06 the operator was structurally out of the loop. We add a **second funnel**, the strategy assessment (`/assess-strategy`), that runs a candidate through everything without short-circuiting and emits one comprehensive report for human judgment. It is an **instrument, not a brake**: it accepts any config — including dead ones (autopsy) and settled ones (deployment read) — evaluates every firewall gate *for information*, and never emits a verdict, never marks a config dead, never settles or advances anything. The only road to TRADABLE remains the firewall (ADR 0008 untouched).

## The battery (quant-designed, 2026-06-12)

One expensive spine plus cheap complements, ~55–65 min total: a **single 25-year walk-forward** (2000–2025 — identical in kind to the firewall's binding 25y aggregate layer; its additive monthly buckets recover the GFC/COVID mandate slices), a **continuous 25-year backtest** (the real un-stitched equity path + sector breakdown — the WF stitch omits IS-window drawdowns), **Monte Carlo** on the stitched OOS trades, and the **deflated-Sharpe flag**; plus a **Random-ranker baseline** only for permissive-entry + ranker-selects candidates. The assessment **never re-fires the separate firewall blocks** — Block A/B/C-range slices of the spine are labeled proxies (each firewall block is its own walk-forward with its own IS-anchoring and window phasing); where real block JSONs exist they are shown beside the proxies.

## The C-span exposure (deliberate overrule)

The spine's 2021–2025 windows are shown **in full per-window detail**. The quant's recommendation was C-coarse (SURVIVED / CATASTROPHIC / trade-count only), because per-window C numbers for a config the operator will redesign from permanently contaminate the platform's only true OOS block **for the whole premise family** (ADR 0007), invisibly to the deflated-Sharpe N. The operator (sole user, steers risk appetite) overruled with the quant's disclosure floor: every C-span section carries a contamination stamp, and the lineage gets a permanent **operator-eyeballed-C annotation** surfaced by the deflated-Sharpe readout — the leak is loud and counted, never hidden. Consequence, accepted: from a family's first assessment onward, the firewall's Block C verdict is decorative for that family.

## The three couplings (and only three)

1. Every assessment run is a **firewall trial** in the deflated-Sharpe ledger (it observes binding-grade stitched-OOS Sharpes on the shared tape; hidden N is the sin — ADR 0014). The updated flag is displayed in every report, so the cost of iterating variants through assessments is visible in each one — disclosure-based discipline where the validation funnel uses structural refusal.
2. The **operator-eyeballed-C annotation** on the premise family.
3. The **assessment decision log** — the operator's post-report decision (redesign / send to firewall / paper-trade / deploy-at-own-risk / shelve) appended to the assessment ledger.

## Considered options

- *Report-only inside the validation funnel (kill-capable)* — rejected: entangles an informational instrument with the brake's state machine; the operator explicitly wants a separate human-judgment track.
- *C-coarse disclosure* — the quant's recommendation; overruled as above.
- *Fully off-book (no couplings)* — rejected: silently falsifies the deflated-Sharpe N.

Persistence: `strategy_exploration/assessments/<candidate>/` (request JSON per ADR 0017, assessment markdown, append-only JSONL ledger). Regime decomposition is computed in the backend (per-regime edge ± date-clustered SE) — see ADR 0023.
