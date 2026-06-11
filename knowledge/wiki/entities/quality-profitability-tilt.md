---
type: entity
title: Quality / profitability tilt
summary: REJECTED at Block A; levered-pivot timing-dead. A real ~17.8% CAGR / ~0.9 Calmar quality premium, below the unlevered floor and un-crisis-defendable (washout trigger too slow). Shelved, validated.
status: stable
tags: [candidate, fundamentals, quality, rejected, shelved]
sources: ["docs/adr/0019-fundamentals-are-point-in-time-reference-data-and-quality-is-a-cross-sectional-percentile.md", "docs/adr/0020-same-day-cohort-ranking-is-a-first-class-stockranker-capability.md", "docs/adr/0021-block-a-gfc-mandate-is-a-crash-survival-test-not-a-strict-positive-gate.md", "https://github.com/skrymer/trading/issues/150", "https://github.com/skrymer/trading/pull/152", "knowledge/wiki/sources/2026-06-11-quality-tilt-condition-screen-killtest.md", "knowledge/wiki/sources/2026-06-11-quality-tilt-random-null-screen.md", "knowledge/wiki/sources/2026-06-11-quality-tilt-trend-leg-ablation.md", "knowledge/wiki/sources/2026-06-12-validate-quality-profitability-tilt.md", "knowledge/wiki/sources/2026-06-12-levered-quality-lag-check-prereg.md"]
related: ["[[beta-delivery]]", "[[participate-and-lose]]", "[[gjallarhorn]]", "[[long-premise-in-narrow-leadership]]", "[[the-funnel]]", "[[pead]]", "[[george]]", "[[aliased-regime-sensitivity]]", "[[thinning-not-selecting]]", "[[2026-06-11-quality-tilt-condition-screen-killtest]]", "[[2026-06-11-quality-tilt-random-null-screen]]", "[[2026-06-11-quality-tilt-trend-leg-ablation]]", "[[2026-06-12-validate-quality-profitability-tilt]]", "[[2026-06-12-levered-quality-lag-check-prereg]]", "[[purpose]]"]
updated: 2026-06-12
---

# Quality / profitability tilt

The first **live directional candidate** after the funnel emptied — a cross-sectional long premise that
selects on a **non-price, persistent** variable (fundamental gross profitability). Screened, built, and run
to a **terminal verdict**: REJECTED at Block A, levered pivot timing-dead (see Status). The **first candidate
to ever run a real Block A firewall**. Strategy-neutral name (only the assembled strategy earns a Norse name).

## Premise

Long the highest-quality names in the universe, where quality = **Gross Profitability** (Novy-Marx,
`grossProfit_TTM / totalAssets_asof`). The selection variable is fundamental, slow-moving, and
*orthogonal to recent price path* — which is the whole point: every earned-dead long class
([[participate-and-lose]] / [[beta-delivery]]) conditioned on **price state**, which in a strong tape is
just market direction. A fundamental quality rank is not a price transform, so it cannot inherit SPY
through the entry the way [[george]] / [[mrm]] / [[pead]] did.

## Why it might survive narrow leadership (the load-bearing claim)

**Narrow-leadership tape is a *tailwind*, not a wall** — the first premise for which this is true. Narrow
leadership *is* capital concentrating into a few high-profitability, cash-generative mega-caps; that is
exactly the cross-section a quality tilt harvests. The regime that made every breakout / pullback /
RS-momentum premise *participate-and-lose* is the regime in which a quality keep-set converges onto the
names actually receiving the flow.^[inferred — quant mechanism, not yet measured] Quality is also
*persistent* (the documented source of the premium), so the holding-window risk is "the market falls"
(beta, handled by cash) not "my signal evaporates" (alpha decay).

## Locked design (ADR 0019 + quant consult)

- **Gate (L2):** a market-wide, survivorship-free **quality percentile** of GP/TA (TTM gross profit /
  point-in-time total assets), persisted per quote row in Midgaard — mirrors the RS-percentile pattern
  ([[component-firewall]] sibling, ADR 0009). Eligibility = top-quintile (`qualityPercentile ≥ 80`),
  fail-closed on null. Single baked metric (iterate = re-run the pass).
- **Ranker:** `FundamentalQualityRanker` = `0.5·z(GP/TA level) + 0.5·z(margin-trend YoY)`, intra-subset
  (Udgaard, from raw `Stock.fundamentals`) — the gate-vs-ranker split of ADR 0009. Being a *blended*
  cross-sectional ranker, it needed a new engine capability — **`StockRanker.rankCohort`** (ADR 0020), a
  same-day-cohort ranking mode (per-stock default keeps every existing ranker byte-identical). See
  [[2026-06-10-quality-tilt-build-complete]] for why a single-leg cross-sectional z would have been a no-op.
- **Point-in-time:** every fundamentals read gates on EODHD `filing_date ≤ tradingDate` (never
  `fiscalDateEnding`) — the earnings `reported_date` guard; restatement residual accepted + documented
  (CONTEXT.md *Point-in-time fundamentals*).
- **Assembly:** gate + ablatable `price > SMA200` trend leg, `FundamentalQualityRanker`, maxPositions 15,
  entryDelayDays 1, ATR sizer (unlevered first pass), OR-exits (quality < 60 hysteresis / SMA200 break /
  ATR trail). Build spec: GitHub issue #150; architecture: PR #151.

## Status — REJECTED, then levered-pivot timing-dead. Premise EXHAUSTED, assets shelved.

The full arc (2026-06-10 → 2026-06-12): cleared **both cheap beta-delivery tells** at screen stage
(flat-SPY-tertile kill-test + Random-ranker null PASS-at-5 — the *first* candidate to clear either, let
alone both; [[2026-06-11-quality-tilt-condition-screen-killtest]], [[2026-06-11-quality-tilt-random-null-screen]]),
dropped the trend leg as tail-harmful ([[2026-06-11-quality-tilt-trend-leg-ablation]]), then **REJECTED at the
Block A firewall** and the only return-lifting pivot proved **timing-dead**:

- **Block A REJECTED (2026-06-12)** — 5 binding gates fail: G1 CAGR 18.57% < 25%, G2 DD 36% > 25%, G3
  worst-window DD 30% > 20%, **G6 2008 GFC edge −0.99% < 0 (root cause)**, G15 Calmar 0.512 < 1.5. But
  **G16 SPY-baseline PASSED** (Calmar 0.512 vs SPY 0.164 — beats passive **3.1×**), 9/11 windows positive,
  Sharpe 0.83. So **NOT beta-delivery and NOT no-edge** — a real edge that (a) fails the 2008 GFC defense
  and (b) misses the unlevered return floors. Excise-2008: heals G6/G3/G2 but G1 (~17.8% CAGR) and G15
  (~0.9 Calmar) are **structural** — the unlevered gross-profitability premium is simply below the floor.
  Full read: [[2026-06-12-validate-quality-profitability-tilt]].
- **Levered pivot (leverage for G1 + washout cash-circuit-breaker for G15) — TIMING-DEAD (2026-06-12).** A
  pre-registered empirical lag check killed it for the cost of a query: the frozen washout trigger fires
  ~10 days into a crash *at ~−19% drawdown* (2008/2020), so a 1.5× book is already **~−28%** before going to
  cash — blowing G2/G3 at any tradable leverage. **The washout classifier is a crisis-*bottom* detector, not
  a crisis-*avoidance* circuit-breaker** ([[gjallarhorn]]'s +22σ bottom-timer strength is exactly why it's
  useless as a top-exit). Full read: [[2026-06-12-levered-quality-lag-check-prereg]].

**Disposition:** the quality premise is **fully exhausted as a tradable candidate** — unlevered below the
floor, levered+washout timing-dead, no third path without re-tuning the frozen crisis trigger (forbidden).
The quality gate + `FundamentalQualityRanker` + deterioration exit are **shelved, validated assets** (the
ADR-0019/0020 data + cohort-ranking infrastructure outlive this candidate). Next: a fresh premise class.

## Pre-registered tests — steps 1 & 2 PASSED; trend-leg ablation is the active step

1. ✅ **PASSED (2026-06-11)** — `/condition-screen` the gate alone, 300-sym sanity universe. The
   **flat-SPY-tertile rule** ([[beta-delivery]]) held: flat `meanLift` solidly positive at 10d+20d, no
   regime sign-flip; ARS sweep clean monotone (no aliasing, [[aliased-regime-sensitivity]]); Jaccard vs
   RS70 ≤0.25 (not RS in disguise). Full read: [[2026-06-11-quality-tilt-condition-screen-killtest]].
2. ✅ **PASSED at 5 seeds (2026-06-11)** — the **Random-ranker null** at `/strategy-screen`, full universe,
   bare gate, 2005–2015. `FundamentalQuality` beats Random on every median (edge 0.802 vs 0.635, CAGR 19.0
   vs 14.0, Sharpe 0.88 vs 0.69, Sortino 1.28 vs 0.98). Strict clean-separation not literally met (Random
   s42 edge 0.860, s45 CAGR 22.20 poke above the candidate median) → quant adjudicated PASS on the
   **variance-collapse signature** (candidate edge spread 0.17 vs Random 0.58, worst candidate seed above
   Random median, equal drawdown envelope). First candidate to clear the random-ranker tell that
   [[george]] / [[mrm]] / [[multifactor-residual-momentum]] all failed. Full read:
   [[2026-06-11-quality-tilt-random-null-screen]].
3. ✅ **DONE (2026-06-11) — trend leg DROPPED.** The ablation (Random-null *with* `priceAboveSma200`) showed
   the trend entry leg is **tail-harmful adverse selection**, not a help: it halves trades, lowers CAGR in
   both arms, **doubles aggregate max-DD (~30%→~50%) and quadruples the 2008 worst-window loss** (candidate
   2008 edge −0.79 → −3.56) — it whipsaws into the bear. Critically, the ranker's **edge gap over Random is
   invariant** (+0.168 bare ≈ +0.160 trend) → the edge is the *ranker*, not the gate; this is now the
   load-bearing evidence (the variance-collapse signature is demoted to corroborating — it *inverted* under
   thinning, partly a sample-size artifact). Carry the **bare-gate config** to Block A. Full read:
   [[2026-06-11-quality-tilt-trend-leg-ablation]].
4. ❌ **REJECTED at Block A (2026-06-12)** — bare-gate config, `/validate-candidate`. 5 binding fails (G1
   CAGR 18.57%, G2/G3 DD 36%/30%, **G6 2008 edge −0.99% — root cause**, G15 Calmar 0.512); G16 SPY-baseline
   PASSED (beats SPY 3.1×). The 2008 GFC window drives G6/G3/most-of-G2; G1/G15 survive its excision
   (structural). The inline SMA200-break script promotion was never needed (path B: firewall-first → no
   wasted G14 build). Full read: [[2026-06-12-validate-quality-profitability-tilt]].
5. ❌ **Levered pivot TIMING-DEAD (2026-06-12)** — the one return-lifting path (1.5× leverage for G1 + a
   book-level washout cash-circuit-breaker for the G15 Calmar denominator) failed its pre-registered lag
   check: the frozen washout trigger fires ~10 days into a crash at ~−19% DD, so a 1.5× book is ~−28% before
   reaching cash (blows G2/G3). The washout classifier is a crisis-*bottom* detector, not a top-exit. Full
   read: [[2026-06-12-levered-quality-lag-check-prereg]]. **Premise exhausted.**

### Pre-registered decision rule — Random-null (locked 2026-06-11, before firing; quant GO-WITH-CHANGES)

Config: `quality-profitability-tilt.request.json` (the **bare-gate primary pass** — `priceAboveSma200`
trend leg DROPPED so the ranker is the only cross-sectional selector; quant's cleaner-isolation call).
2005–2015, 36/12/12 (7 OOS windows), full universe, ATR-risk 1.25%/2.7·ATR, maxPositions 15.

- **Both arms swept on seeds {42,43,44,45,46}** — candidate (`FundamentalQuality`) *and* Random, each a
  5-point cloud (the candidate is NOT a single point: it collapses to pure tie-break jitter on
  degenerate all-`z=0` cohort-days, so it is seed-sensitive). 10 runs.
- **Discriminator (locked):** the candidate-cloud median must beat the Random-cloud on **per-trade edge
  AND blended CAGR AND positive-window count** (NOT win-rate/WFE).
  - candidate median **≤ Random median** on edge or CAGR → **REJECT / INCONCLUSIVE**, stop at 5 (a
    sub-median point can't be rescued by more seeds).
  - candidate **clearly above all 5 Random points** on edge AND CAGR AND positive-windows → **PASS** at 5.
  - candidate **inside the Random band** (p95 becomes load-bearing) → **top up both arms to 20 seeds**
    {47..61} before any verdict.
  - **RESULT (2026-06-11):** the outcome fell in the *intermediate* case the binary rule didn't name —
    candidate above the Random median (not a reject) but not clearly above all 5 Random points (not clean
    separation), yet *not* inside the band either (tighter cloud sitting above the Random median, two single
    high Random draws poking in). Quant adjudicated **PASS on the variance-collapse signature** rather than a
    mechanical top-up-to-20 (more seeds would only sharpen a separation the cloud *shape* already shows).
- **Mandatory diagnostics** (else the null is uninformative): per-arm turnover / holding-period;
  `cohortSize` + `rankInCohort` distribution on fills (confirm the ranker actually bound, i.e. cohort
  routinely > 15/day); degenerate-day rate (a high rate means a near-tie reads as "ranker rarely had
  usable spread," not "signal dead" — guards a false REJECT).
- If it PASSES → run the **trend-leg ablation** (same config + `priceAboveSma200`) to measure what the
  trend leg adds and check it doesn't merely thin ([[thinning-not-selecting]]).
- ⚠ The inline `script` SMA200-break exit must be promoted to a first-class condition before any
  TRADABLE claim (G14); deferred until the candidate survives this gate.

## Most likely death (pre-mortem) — and how it actually died

The pre-mortem predicted **beta-delivery**: the top-quality names *are* the Mag-7 ⇒ the gate reconstructs a
cap-weighted mega-cap basket ⇒ it delivers beta (flat tertile ≤ 0). Secondary: early-2000s coverage.

**Both predictions were wrong.** The flat tertile stayed solidly positive (kill-test PASS), the ranker beat
Random (null PASS), G16 PASSED (beats SPY 3.1× — *not* beta), and coverage cleared 11× over. The premise
died a *different* death the pre-mortem didn't foresee: it has a **real, non-beta edge** but **(a) can't
defend the 2008 GFC** (G6, the crisis-undefended [[participate-and-lose]] sub-type) and **(b) sits below the
unlevered 25%/1.5 floor** in its good years — and the crisis-defense pivot is **timing-dead** because the
only available crisis signal (the washout classifier) is a bottom-detector, not a circuit-breaker. The
abort-discipline lesson held in a new form: a REJECTED candidate is not patched/re-run — the levered pivot
was a *new* pre-registered candidate, killed at a pre-build lag-check gate.

## Failure modes to watch

[[beta-delivery]] (primary) · [[aliased-regime-sensitivity]] (the ARS sweep) · [[thinning-not-selecting]]
(if the trend leg only thins) · [[crisis-timer-cadence-ceiling]] (N/A — high cadence by construction).

## Related

[[2026-06-10-quality-tilt-scoping-and-design-lock]] · [[beta-delivery]] · [[participate-and-lose]] ·
[[long-premise-in-narrow-leadership]] · [[pead]] · [[purpose]]
