# Backtesting funnel — claims & open questions to verify (deep-research seed)

_Created 2026-06-04. Seed for an external deep-research pass verifying our strategy-validation funnel against best-practice quantitative methodology. Source: `BACKTESTING_FUNNEL.md` flagged items + 2026-06-04 session additions. The funnel doc itself names this pass as pending ("external deep-research verification of the approach against best-practice quant methodology")._

## The research question
**Does this funnel match best-practice quant validation methodology — and where is it wrong, miscalibrated, or missing a step?** For each claim below: is it sound, what does the literature say about the threshold/method, and what would a rigorous shop do differently?

## Context (constraints the funnel operates under)
Long-only, daily-timeframe, single-name US-equity backtest engine; survivorship-correct ~3,900-symbol universe (2000-2026); no shorting, no intraday, perfect fills (no slippage/commission modeled); single operator. **Goal: find ONE standalone tradable strategy clearing a 30% CAGR floor.** The funnel is the pipeline: `/condition-screen` (design-time) → `/strategy-screen` (10y triage) → `/validate-candidate` (3-block firewall) → promotion/G14 → `/monte-carlo` → 30% floor.

---

## A. Validation-harness design
- **A1. Walk-forward screen window is fixed at 2005-2015, IS/OOS/step 36/12/12 → 7 OOS windows.** *Verify:* is 7 OOS windows enough for cross-window stability inference? Is a single fixed window (vs rolling/anchored/combinatorial purged CV) defensible, or is it over-fit to one history? Should we use **Combinatorial Purged Cross-Validation** (López de Prado) instead?
- **A2. 3-block firewall: Block A 2000-2014, Block B 2014-2021H1 (COVID-incl), 25y aggregate = binding; Block C 2021-2025 = informational only.** *Verify:* is holding out the *most recent* block as merely "informational" (never a binding fail) sound, or backwards (recent data is the most decision-relevant)? Are fixed calendar blocks the right regime partition?
- **A3. ⚠ Rare-event limitation (this session).** A per-window walk-forward **cannot validate a strategy that trades <~1 event/yr** — most OOS windows have zero trades, so WFE/per-window stats are uncomputable. We treat this as an automatic disqualification ("cadence ceiling"). *Verify:* is per-window the wrong harness for rare/event-driven premises? What is best practice for validating low-frequency/event strategies (pooled event studies, stationary bootstrap over events, Bayesian)?
- **A4. No slippage/commission/borrow modeling.** *Verify:* how much does this inflate edge for a daily-rebalanced equity strategy, and what's the standard haircut?

## B. Gate thresholds (are these calibrated, or folk numbers?)
- **B1. Screen G1:** pooled OOS per-trade edge ≥ **0.10 × riskPercentage**. **G2:** stitched OOS **Sharpe ≥ 0.7**. **G3:** ≥**5 of 7** windows edge>0 AND median window edge>0. **G4:** GFC-window maxDD ≤ **2× median** window. **G5:** if >50 variants swept, raise G1 to 0.12×risk AND require 6/7. *Verify each:* literature basis for a 0.7 Sharpe screen bar, the edge floor scaling, and the GFC-stress multiple.
- **B2. Firewall G11 cross-block edge decay:** `edge_B ≥ 0.5 × edge_A`. *Verify:* is 50% retention the right persistence bar?
- **B3. G13 parameter robustness:** verdict must survive **±1 step on every discrete tunable, ±10% on every continuous tunable**. (Step sizes "pending sign-off in places.") *Verify:* is ±1 step / ±10% the right robustness neighbourhood, and is one-at-a-time (vs joint) sufficient?
- **B4. 30% CAGR floor [operator appetite, not derived].** *Verify:* is a hard CAGR floor a sound final gate, or should it be risk-adjusted (MAR/Sharpe/Calmar)? What CAGR is realistically attainable, net of costs, for a long-only daily equity strategy without leverage?
- **B5. `/condition-screen` flag thresholds are "explicitly uncalibrated until a corpus of known-distinct/known-duplicate conditions exists"** (Jaccard >0.5 "redundant" / >0.7 "near-clone"; selectivity ≥33% amber / ≥60% red; ARS 2×SE rule). *Verify:* are these reasonable defaults?

## C. Anti-data-mining interlocks
- **C1. G10 design isolation** — config (`config hash`) frozen across all blocks; no per-block tuning. **C2. Dead-config rule** — a REJECTED config and its ±1 neighbours may never be re-run (ADR 0008, enforced by a non-executing state-machine). **C3. The IS-fitting line** — may NOT fit a regime/parameter to an observed bad window; MAY add an axis motivated *independently* of the observed failure. **C4. Lineage** — a successor to a rejected premise requires a recorded analysis judging it structurally distinct. *Verify:* do these adequately control the multiple-comparisons / data-snooping problem across a multi-candidate search? Is there a **deflated Sharpe ratio / multiple-testing correction** we should apply across the number of candidates+variants tried (we've run ~10 premises × many variants)?
- **C5. G14 implementation invariance** — a promoted condition's trade list must diff-match the inline-script research version by (entry_date, symbol) over 25y. *Verify:* sound?

## D. Null baselines / benchmarking
- **D1. G-RANDOM** — a permissive-entry + ranker-selects candidate must beat a byte-identical **Random-ranker** baseline on blended CAGR AND per-trade edge AND positive-window count (else the "edge" is entry-universe beta). *Verify:* is Random-ranker the right null for isolating selection skill?
- **D2. Conditional within-regime null (this session, NEW).** For a market-*timing* rule, the null draws random entries from the *same comparable-stress regime population* (not uniform days) at matched rate, 20 seeds; candidate must beat null **p95** on per-trade edge (≥~2σ). *Verify:* is a within-regime conditional null the correct way to separate timing skill from regime beta? Relation to standard event-study abnormal-return / matched-sample methods?
- **D3.** No absolute benchmark gate vs **buy-and-hold SPY** (total return) is currently enforced. *Verify:* should every candidate be required to beat buy-and-hold on a risk-adjusted basis as a baseline?

## E. Failure-mode diagnostics (are these real, named correctly?)
- **E1. Lottery / regime-detector** — edge concentrated in 1-2 OOS windows + several negative windows; geometric compound of the lumpy sequence is the "true" number. **E2. Aliased Regime Sensitivity (ARS)** — non-monotone pass/fail across a parameter's ±1 neighbourhood + per-window edge sign-flips at stable trade counts ⇒ the parameter dimension is structurally inappropriate. **E3. Participate-and-lose in narrow leadership** — full trade counts, clustered losses in narrow-breadth tape; a scalar market gate only thins. **E4. Capped premise / ties-Random.** **E5. Capital-aware ablation confound** — single-realization LOO/add-one ablation metrics are sizer/concurrency-contaminated; blended CAGR ~monotone in trade count. *Verify each:* are these recognized phenomena under standard names, and are our diagnostic signatures correct?

## F. This session's additions (verify + consider for the funnel doc)
- **F1. Crisis-timer cadence ceiling** (= A3) — rare-event premises funnel-disqualified pre-screen.
- **F2. Conditional within-regime null** (= D2).
- **F3. Breadth-metric calibration finding** — our internal `breadthPercent` is a *short-horizon oscillator* (mean ~42, ≤15% ≈ 7th percentile, touched every year), NOT a slow %-above-200DMA series; a single low touch is routine, so crisis detection needs *sustained-duration* depth, not a level. *Verify:* general lesson — validate a regime indicator's distribution before gating on a level.
- **F4. Overlay / portfolio testing.** The engine runs ONE strategy (one ranker/sizer/exit); we test overlays via an **offline two-curve equity blend** (fixed sleeves). Dynamic regime-switching is inexpressible (long-only ⇒ defense=cash, ADR 0010). *Verify:* is offline fixed-sleeve blending a valid way to evaluate a multi-strategy book, or do we need true portfolio backtesting? Best practice for evaluating a defensive/overlay sleeve?

## G. Explicitly open / unverified
- **G1. "Narrow-leadership twin" hypothesis** (⚠ UNVERIFIED) — cross-sectional RS-momentum rotation is *assumed* to die the breakout's death; never actually run. *Verify:* is the analogy sound, or could narrow leadership *feed* momentum persistence (inverting the claim)?
- **G2. Premise-space exhaustion claim** — we assert the long-only daily no-fundamentals space expresses edge as price-derived state (return-change exhausted; level-anchoring tested/dead), event-date underreaction, flow/calendar timing, and defensive overlays. *Verify:* is this partition complete? What proven premise classes are we missing for this engine type?

---
_Use: feed to `/deep-research` as the question set; weave answers back into `BACKTESTING_FUNNEL.md` (resolve the `[open]` / ⚠ tags) and the relevant skills/ADRs after a quant wording-review._
