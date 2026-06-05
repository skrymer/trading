# Multiple-testing is a reported deflated-Sharpe flag, not a binding gate

The funnel runs a multi-month, ~10-premise × many-variant search against one 2000–2025 dataset, with no explicit multiple-testing correction — the dominant gap surfaced by the deep-research audit (López de Prado DSR, Harvey-Liu, Bailey). This ADR locks in **how that correction enters the funnel: as a *reported* Deflated-Sharpe-Ratio (DSR) flag at the firewall stage — never a binding auto-reject** — and how the trial count `N` is defined. It extends ADR 0008 (the non-executing state-machine as a data-mining brake): ADR 0008 stops a *known-dead* config from re-running; this flag quantifies how much of a *surviving* config's headline Sharpe is plausibly max-of-search luck.

## The choice

When `/validate-candidate` records a firewall verdict, the state-machine computes a DSR flag and writes it into the dossier `RECORD` event for the `firewall-analyst` readout:

- **Status: FLAG only, never an auto-reject.** It tells the operator how much of a survivor's stitched-OOS Sharpe is plausibly best-of-search inflation; it does not kill the candidate.
- **Scope of N: GLOBAL** across every firewall-reaching config in the whole search program, all premise classes. Shared *data* makes them one multiple-testing experiment; a different *hypothesis* does not create a separate test family (Harvey-Liu).
- **Population of N: firewall `RECORD` events only** — distinct `config_hash` with a `/validate-candidate` verdict (TRADABLE / PROVISIONAL / NEAR_MISS / **REJECTED** / INCONCLUSIVE all count; a rejected firewall run still consumed a look at the binding metric). Screen survivors are excluded — they competed on a different, cheaper estimator (WFE/edge), not the stitched-OOS Sharpe being deflated. `FIRED`-but-no-`RECORD` does not count (no Sharpe observed).
- **Effective-N, not flat count.** Cluster firewall configs by **dossier lineage** (one dossier file = one candidate = one lineage; a structurally-distinct successor is a new file per ADR 0008's DISTINCT gate). `N_high` = number of dossier files with ≥1 firewall RECORD; `N_low` = a Harvey-Liu correlation-haircut `1 + (k−1)(1−ρ̄)` collapsing within-lineage near-clones (±1 G13 neighbours, exit/sizer tweaks) toward independence. The DISTINCT gate thus doubles as the trial-counter.
- **Report a band, not a scalar.** The DSR is reported across `N ∈ [N_low, N_high]`, with the **itemized lineage list** always published alongside. The band spans only the irreducible lineage-correlation uncertainty (#effective-N), *not* the scope/population questions, which are committed. Interpretation: **amber** if the most-deflated (`N_high`) endpoint pushes PSR below 0.95; **red** if even the least-deflated (`N_low`) endpoint does.

## Why a flag and not a binding gate

DSR's `E[max Sharpe]` null assumes `N` independent (or exchangeable) trials drawn from a known design space — a grid sweep. Our search is a sequential, human-pruned *tree*: most variants die at condition- or strategy-screen before producing a firewall Sharpe, and survivors are lineage-correlated. There is **no honest scalar N**, so a binding gate keyed on `N` would just relocate the folk-number problem onto a fictional input. As a *flag*, an approximate N is informative; as a *gate*, it would be false precision with a tradable/reject decision riding on it. The iid-with-known-variance null is also a poor model of an adaptive operator who kills lineages on judgment — so the flag is presented as "how much of this Sharpe is plausibly best-of-search luck," **directionally informative but quantitatively soft**, not a calibrated p-value.

## Why not the obvious alternatives

- **Flat-count distinct `config_hash`es** → over-deflates. The penalty would be driven by how many sizer swaps and ±1 neighbours one happened to try — a researcher-effort artifact, not a property of the strategy. A flag that moves on bookkeeping busywork is noise. Hence effective-N by lineage clustering.
- **BHY-FDR / Bonferroni** (the Harvey-Liu factor-zoo instruments) → wrong tool here. They need a clean family of simultaneous p-values; a tree-pruned, adaptively-walked search does not produce one. DSR's best-of-N framing fits a "report the survivor" selection process; FDR fits a fixed battery of pre-registered tests.
- **Per-premise / per-lineage scope for N** → a data-snooping degree of freedom. Resetting the counter each time the hypothesis is renamed is exactly the latitude the correction exists to remove. N is global.
- **Counting screen survivors toward N** → mis-specified, not conservative. DSR deflates the firewall Sharpe; only trials for which that statistic was computed are draws of it. Screen survivorship bias on *which* configs reach the firewall is a separate effect, already partly defanged by the dead-config and design-isolation rules — do not double-count it by inflating N.

## Relationship to G5

This flag **augments, does not replace, the screen-stage G5 gate** (>50 variants → tighter G1/G3). G5 is *binding* at `/strategy-screen` because multiple-comparison correction there must be early: noise survivors pre-select for upward noise before reaching the firewall, and downstream tightening cannot recover from biased screen selection. The DSR flag is a *downstream, non-binding* readout at the firewall answering a finer question (how inflated is *this* survivor's Sharpe). Two mechanisms, two stages — the screen tripwire stays binding; the firewall flag is informational.

## Where the logic lives

Split along the engine/search boundary (consistent with "keep calculations in the backend" and the engine's search-agnosticism):

- **Engine (`RiskMetricsService`, testable Kotlin):** pure `deflatedSharpe(observedSharpe, nEff, trialSharpeVariance, skew, kurtosis, nObs)` / `probabilisticSharpe(...)`, exposed via a thin `POST /api/risk/deflated-sharpe`. The engine takes `nEff` and `trialSharpeVariance` as **parameters** — it never learns what a "trial" is.
- **State-machine (`scripts/`, the dossier owner):** a `registry.collect_firewall_trials(dossier_dir)` query (parallel to `collect_dead_hashes`), the lineage clustering, `[N_low, N_high]`, `trialSharpeVariance` from the stored firewall Sharpes, the endpoint call, and writing the flag onto the RECORD event.

## Consequences

- No dossier schema change: the trial population is derivable from existing `RECORD` events (`hash`, `target`, `verdict`) grouped by file; the only new persisted datum is the flag itself on the RECORD event.
- Disclosure is the point: the itemized lineage list is published with every flag — *hidden N is the sin, not uncertain N* (Bailey-Borwein-López de Prado-Zhu). This ADR is part of that disclosure.

## What this does NOT decide

- **A continuous DSR-style correction at the *screen* stage** (upgrading G5's crude >50 step). Possible future refinement; needs screen-stage Sharpe variance (the screen's metric is WFE/edge, not firewall Sharpe). Out of scope; G5's binary tripwire stays as-is.
- **Auto-rejection on the flag.** Explicitly rejected here; revisit only if a defensible binding N ever exists (it does not for a human-pruned search).
