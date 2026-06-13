# The strategy assessment emits descriptive applicability ratings (broad / regime / sector) and its decision names the targeted dimension

ADR 0022 gave the assessment one comprehensive report ending in a single operator decision drawn from a flat five-bucket vocabulary (redesign / send-to-firewall / paper-trade / deploy-at-own-risk / shelve). That vocabulary assumes a strategy is **one thing** you accept or reject wholesale. The operator's regime-specialist thesis (2026-06-13) rejects that assumption: an all-weather strategy that works across every regime is not the goal — the goal is a **stable of per-regime / per-sector specialists** routed by a classifier. Under that frame a single decision is lossy. The motivating case is [[minervini-vcp-breakout]]: on clean PRD data it is **REJECTED as an all-weather book** (sub-floor, SPY-baseline FAIL) yet shows a **positive THRUST-conditional edge** — a flat `shelve` collapses the two readings and discards the regime signal.

So the assessment now emits, as the report's headline summary, a set of **applicability ratings** along three deploy-targeting dimensions, and the operator's decision **names the dimension it targets**. This refines — does not replace — ADR 0022's third coupling (the assessment decision log). The assessment remains **non-adjudicating**: ratings are descriptive characterizations (the same epistemic class as the gate table), never verdicts. The firewall (ADR 0008) remains the only road to TRADABLE.

## The ratings

Three peer dimensions, each a structured block `{label, evidence (edge ± date-clustered SE, N), confirm-path, risk caveat}` on one shared 4-value scale:

- **Broad** — all-weather, all-sector application (the firewall's domain; the assessment characterizes it, the firewall adjudicates it).
- **Regime** — per **gateable** regime only: CRISIS and THRUST. GRIND / NARROW / CHOP are `unrateable` by construction (below the read-out's resolving power — ADR 0024).
- **Sector** — per sector, from the continuous run's `sectorStats`.

Scale: **favourable** (positive edge clearing the bar — *a deployment hypothesis carrying its confirm-path*) · **neutral** (edge present but not reliably directional) · **adverse** (edge negative or inverted) · **unrateable** (below the evidence floor — the common case). `unrateable` is never collapsed into `neutral`: "we cannot say" (thin-N, a Grade-D regime, contamination) is distinct from "we looked and it is flat."

## The evidence bar (quant-signed, 2026-06-13)

A rating is assigned by a fixed rule, evaluated in this order (so `unrateable` wins ties and dominates in doubt):

1. **unrateable** — `N < 30`, OR the concentration / dispersion guard trips, OR the dimension value is below resolving power (a non-gateable regime label), OR the SE is so wide the sign is indeterminate.
2. **adverse** — rateable AND `edge < −k·SE`.
3. **favourable** — rateable AND `edge > +k·SE` AND premise-consistent AND its mandatory caveats attached.
4. **neutral** — rateable AND `|edge| ≤ k·SE`.

Parameters: a single rateability floor `N ≥ 30` (no second favourable-only floor — thin N dies naturally through the wide SE); `k = 2` for the regime family, **`k = 2.5` for the 11-cell sector family** (where the family-wise false-positive bites); the directional test runs on a **trimmed / robust edge**, never the raw mean, so a tail can never carry a label. The sector dimension additionally requires, for rateability, a **concentration guard** (largest single trade ≤ ~40% of the cell's total P&L — the direct bad-print neutralizer) and **robust-sign agreement** (mean and trimmed edge agree in sign). Multiplicity across the ~13 simultaneous cells is **flagged in the dimension-level caveat**, not hard-corrected — over-correcting a descriptive hypothesis-generator defeats its purpose.

The semantics of `favourable` are exactly *"descriptive-positive, regime/sector beta not excluded"* — a hypothesis, never an attribution and never a validation.

## The within-condition null stays the confirm-path, not the battery

Whether a regime/sector edge is *attributable* (timing/selection skill) versus *regime/sector beta* is answered only by a within-condition null — random entries drawn from the same comparable-stress population at matched cadence (the conditional-within-regime-null rule). That null is **not** run inside the assessment, for three reasons: it is not computable on the existing trade set (those trades are the strategy's, not random draws — it needs ~13 fresh backtests per assessment); running it in-assessment recreates the pull to treat a "passing" regime as a license to gate this config (the rescue trap); and each in-assessment null multiplies deflated-Sharpe trials. So a `favourable` rating's **confirm-path** field *names* the within-condition null as the next step — a **fresh, distinct, regime-/sector-scoped candidate**, validated through the firewall, never a gate bolted onto this config. The battery may carry one cheap enrichment — the **within-strategy baseline contrast** (bucket edge − whole-sample edge, with the clustered SE of the difference) — as a favourable tie-breaker that separates "carries a regime-specific edge" from "inherits the strategy's general edge"; it is explicitly **not** the null (it compares the strategy to itself, not to a naive entry) and never substitutes for the confirm-path.

## Regime-specific constraints on the label

- **THRUST `favourable` carries a mandatory recovery-blind caveat.** The drawdown leg's precedence labels the ~12-month post-crash recovery as CRISIS, so the THRUST bucket structurally omits one of the best windows for long breakouts. The in-sample favourable is therefore conservative, but a THRUST-gated specialist would be **blind to the recovery rip** — the caveat and the confirm-path must state this deployability hole.
- **CRISIS is premise-gated and defaults `adverse`/`unrateable` for a long-breakout premise.** CRISIS is a confirmation that lags topping; a long entry there is a falling knife or V-bottom survivorship, not skill. `favourable` CRISIS is allowable only when the strategy's premise *is* crisis-entry (dip-buy / mean-reversion) and the cell survives the guards; a `favourable` CRISIS on a trend-long is auto-demoted to `neutral` with a survivorship flag.

## The decision

The operator's decision stays a **single terminal act** in the existing five-bucket vocabulary, but it now **names the dimension it targets** (e.g. `redesign` toward a THRUST-regime specialist). The ratings are recorded as a **separate ledger event** from the decision, so the dual-outcome nuance lives in the ratings and the decision remains the one conclusion the ledger treats as terminal.

## Sector ships `unrateable-pending`

The bar's sector test needs per-sector statistics the engine does not yet emit (`sectorStats` has no SE, no trimmed edge, no max-single-trade share). Until issue #167 adds them, the **entire Sector dimension is rated `unrateable-pending-instrumentation`** — the honest state, not a blocker on shipping the redesign. Broad and Regime are fully live now.

## Considered options

- *Ratings replace the operator decision* — rejected: re-introduces an adjudicator and removes the operator's terminal act, contra ADR 0022.
- *Genuinely independent per-dimension decisions* — rejected: over-structures the ledger; one decision naming its dimension preserves the nuance without a decision matrix.
- *Run the within-condition null inside the battery* — rejected: ~13 fresh backtests per assessment, multiplies deflated-Sharpe trials, and recreates the rescue pull (ADR 0023). Kept as the confirm-path.
- *Keep the single flat decision (ADR 0022 unchanged)* — rejected: it is the collapse the minervini case exposed.
- *Block the redesign until the sector instrumentation lands* — rejected: Broad + Regime are usable now; Sector ships honestly `unrateable-pending`.

## Consequences

- The report is richer but still emits **no verdict**; the firewall and the operator decision remain the only adjudicators.
- The assessment decision log now carries a dimension; the ledger gains a `RATINGS` event distinct from `DECISION`.
- The Sector dimension is dark until #167; the analyst marks it `unrateable-pending`.
- The rating bar is intentionally **more conservative than free-text prose**: re-rating minervini under it downgrades the earlier "THRUST-favourable" headline to a borderline `neutral` / weak hypothesis (THRUST edge +7.45 ± 3.80 → t ≈ 1.96, a hair under the k=2 bar) — the marginal t-stat is the brake that insists on the confirm-path before any conviction.
- Refines ADR 0022 (the decision-log coupling); consumes ADR 0023/0024 (gateable labels, rescue-forbidden) unchanged.
