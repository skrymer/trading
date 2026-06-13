# Strategy Assessment — Minervini VCP Breakout

| | |
|---|---|
| **Candidate** | `minervini-vcp-breakout` (EX-ATR20 × SectorStrengthMomentum) |
| **Config hash** | `81a1d38ee0a6` |
| **Report date** | 2026-06-13 |
| **Environment** | **PRD** (`:9080`), clean universe, udgaard **1.0.93** |
| **Funnel** | `/assess-strategy` — ADR 0022 non-adjudicating battery, **re-rated under ADR 0025** |
| **Continuous backtestId** | `239232fb-81cc-4eb8-8a48-c7805527c128` |

> This report **informs a human decision; it does not make one.** It carries **no verdict**. The
> applicability ratings in §2 are **descriptive characterizations** — the same epistemic class as the
> gate table, never adjudications. Where the firewall's own historical death is named, it is cited as a
> *referenced historical fact*, never issued here as an assessment outcome. The firewall (ADR 0008)
> remains the only road to TRADABLE.
>
> **This re-rate supersedes the prior PRD report** (same battery, same clean numbers, new ADR 0025
> headline structure). The earlier free-text "THRUST-favourable" headline is downgraded by the bar — see §2.

---

## 1. Framing — autopsy of a firewall-DEAD config

**This config is DEAD in the validation funnel.** It died at the Component Firewall on 2026-06-03 (six
binding gates; quant-confirmed, decisive — not a near-miss); the breakout-in-uptrend
entry-time-regime premise class was deprecated after three pre-registered market-gate fixes all failed
(STRIKE 3). **This report informs a redesign, never a re-run.** Nothing here resurrects, settles, or
kills; the firewall's death record stands and is the only road to TRADABLE (ADR 0008 untouched).

### ⚠ This PRD re-run SUPERSEDES an invalidated 2026-06-12 dev run

A 2026-06-12 `/assess-strategy` run on the **dev** universe was **thrown out as artifactual.** Dev's DB
carried un-caught bad prints — split-adjustment failures including **VPI ($65k bar)** and **LAF
($26.4k bar)**; midgaard's integrity validators flagged **~166 symbols**. The contamination inflated
per-trade edge **~24×** (3.7 → 90.9), lifted CAGR **~25 points** (12.9% → 37.7%), made the Monte Carlo
degenerate, and **spuriously PASSED the SPY baseline** (a clean-data FAIL flipped to a pass on phantom
returns). **PRD is clean** — one integrity violation surfaced (**ERX**, a leveraged ETF outside the
STOCK universe, irrelevant to this `assetTypes:[STOCK]` config). The clean PRD numbers below reproduce
the firewall record. **The dev figures were data artifacts; the PRD figures are the strategy.**

### Pre-flight advisories (carried verbatim)

- **No blockers.** Pre-flight passed; battery fired in full.
- **Firewall-DEAD config → autopsy framing** (not refusal). Carried.
- **Random-baseline arm DELIBERATELY SKIPPED** — the entry is a tight 10-condition breakout stack (not
  permissive-entry + ranker-selects), so the byte-identical Random-baseline beta guard does not apply;
  **operator-confirmed.**
- Entry conditions `narrowingRange` / `volumeDryUp` are first-class promoted conditions
  (`/verify-promotion` G14-PASS, 946=946 trades, Jaccard 1.000000) — **no inline-`script` advisory applies.**

### Config under assessment

ATR-risk sizer **1.25% risk / 2 ATR**, `maxPositions 10`, ranker **SectorStrengthMomentum**, `randomSeed
42`, 25y span 2000-01-01 → 2025-01-01, 36/12/12-month walk-forward. Entry: `spyTrendUp` AND a 50/150/200
SMA stack with rising 200 AND within 25% of the 52-wk high AND ≥30% above the 52-wk low AND
RS-percentile ≥70 AND narrowing-range AND volume dry-up AND within 1.5% of the Donchian high AND volume
≥1.3× its 20-day average. Exit: 2-ATR stop OR close below the 50 EMA.

---

## 2. Applicability ratings (the headline — ADR 0025)

The assessment's headline summary: **descriptive applicability ratings** along three deploy-targeting
dimensions, on one shared 4-value scale (**favourable / neutral / adverse / unrateable**). A rating is
assigned by the **fixed evidence bar** (quant-signed 2026-06-13), evaluated `unrateable → adverse →
favourable → neutral` so *unrateable wins ties and dominates in doubt*. The bar is intentionally **more
conservative than free-text prose**: it runs on the directional test `edge` vs `k·SE` (k=2 regime,
k=2.5 sector), never the raw headline. `favourable` means *"descriptive-positive, regime/sector beta
not excluded"* — a hypothesis carrying its confirm-path, **never** an attribution or a validation.

| Dimension | Rating | Headline evidence | t = edge/SE |
|---|---|---|---|
| **Broad** (all-weather) | **`adverse`** | SPY-baseline FAIL (Calmar 0.34 vs SPY 0.56), sub-floor OOS CAGR 12.9%, 44% unrecovered DD, 9/22 negative windows | — (path/return-level, not a bucket t) |
| **Regime — THRUST** | **`neutral`** | edge **+7.45** ± 3.80, N=169 → **t ≈ 1.96**, a hair under the k=2 bar | **1.96** |
| **Regime — CRISIS** | **`adverse`** | edge **−3.05** ± 1.20, N=68 → t ≈ −2.54, reliably negative, premise-consistent | **−2.54** |
| **Regime — GRIND / NARROW / CHOP** | **`unrateable`** | Grade-D — below the read-out's axis resolving power (ADR 0024); not a thin-N problem | n/a |
| **Sector** (all 11 cells) | **`unrateable-pending`** | the bar's sector test needs SE / trimmed edge / max-single-trade-share that `sectorStats` does not emit (issue #167) | n/a |

### Broad — `adverse`

- **Evidence (decisive for the all-weather dimension).** On clean PRD data the all-weather book is
  sub-floor and dispersion-dominated: **OOS CAGR 12.9%** (continuous 14.0%) ≪ the 25% floor; **Calmar
  0.32–0.34** ≪ 1.5; **SPY-baseline FAIL** (strategy Calmar 0.34 vs SPY 0.56, SPY OOS CAGR 18.9%);
  **9/22 negative OOS windows**; a still-unrecovered **~44% drawdown** spanning 2021→2024. This is the
  firewall's documented [[participate-and-lose]] signature reproduced on clean data — the premise bleeds
  fully-deployed through narrow-leadership chop (§3, §4).
- **Confirm-path.** The firewall is the only adjudicator of this dimension, and it has already adjudicated
  it dead. The *broad* dimension is not re-openable by gating or re-running this config; the only road
  forward on the all-weather axis is a **structurally different ENTRY premise** that resolves the
  cross-section in thin tape — a fresh candidate, firewall-validated from scratch.
- **Caveat.** This rating is a characterization of the assessment battery, **not** the firewall's verdict
  (which is its own, historical, and unchanged). The low SPY beta (corr 0.20, β 0.21) means this is
  *participate-and-lose, not [[beta-delivery]]* — the loss is cross-sectional, not index exposure, which
  is precisely why no market-level gate can rescue it ([[r1-leadership-gap-breakout]], mechanistically proven).

### Regime — THRUST — `neutral` (borderline-positive, NOT favourable)

- **Evidence.** edge **+7.45/trade**, date-clustered **SE 3.80**, **N=169** → **t = 7.45/3.80 = 1.96**.
  This is **a hair under the k=2 bar** and below the multiplicity-aware line, so the bar returns
  `neutral` (`|edge| ≤ k·SE`: 7.45 ≤ 7.60), **not** `favourable`. It is the strongest bucket and it
  corroborates the long-documented Block-B "edge is real in broad-thrust tape" finding on the clean
  trade population — but the marginal t-stat is exactly the brake ADR 0025 installs: **it insists on the
  confirm-path before any conviction.** (Even the within-strategy baseline contrast — THRUST +7.45 vs
  the whole-sample edge 3.65, a +3.80 lift — does not carry it over the k=2 bar; it remains a
  *borderline-positive descriptive reading*, not a deployment-grade signal.)
- **Confirm-path (named, not run here).** Whether this is THRUST-timing skill or THRUST beta is answered
  **only** by a **within-THRUST conditional null** — random entries drawn from the comparable THRUST
  population at matched cadence (the conditional-within-regime-null rule). That null is **not** run in
  this assessment (ADR 0025: it needs ~13 fresh backtests, multiplies deflated-Sharpe trials, and
  recreates the rescue pull). The confirm-path is a **fresh, distinct, THRUST-scoped candidate**
  validated through the firewall — **never a THRUST gate bolted onto this config.**
- **Mandatory caveat (recovery-blind).** THRUST is **structurally suppressed ~12 months post-crash**
  because the drawdown-CRISIS leg takes precedence (2009-Q2/Q3 published CRISIS at 0% THRUST) — an
  accepted trade-off, not a tunable defect. A THRUST-gated specialist would therefore be **blind to the
  recovery rip**, one of the best windows for long breakouts. The in-sample +7.45 is conservative *and*
  carries this deployability hole. Deploy-in-uptrend intent belongs to the leadership-gap regime (ADR
  0010), not THRUST. **Descriptive-only.**

### Regime — CRISIS — `adverse`

- **Evidence.** edge **−3.05/trade**, **SE 1.20**, **N=68** → **t = −3.05/1.20 = −2.54** (precise SE
  1.196 → t ≈ −2.55): reliably negative, clearing the `edge < −k·SE` bar (−3.05 < −2.39). **This long
  breakout premise loses in confirmed crisis** — it does not defend, it bleeds. Premise-consistent:
  breakout longs are stopped out in a confirmed ≥20%-drawdown/washout tape.
- **Confirm-path.** None is needed to *characterize* this as adverse — the sign is robust and
  premise-consistent. (For a premise whose intent *were* crisis-entry, a `favourable` CRISIS would need
  the within-CRISIS null; that is not this premise.)
- **Mandatory caveat (lags-topping / survivorship).** CRISIS is a **confirmation** of "in or recovering
  from a ≥20% drawdown / sustained washout" — **never an early warning; it lags topping phases.** Any
  apparent CRISIS entry that *won* would be V-bottom survivorship, not skill. Here the bucket is squarely
  negative, consistent with the premise; no rescue reading applies.

### Regime — GRIND / NARROW / CHOP — `unrateable`

`unrateable` by construction — Grade-D, **below the read-out's axis resolving power** (ADR 0024), not a
thin-N problem. GRIND (N=92), NARROW (N=158) and CHOP (N=556) are all *well-populated* yet uncitable:
the three cheap daily axes cannot separate these labels. `unrateable` is **never collapsed into
`neutral`** — "we cannot say" (a Grade-D label) is distinct from "we looked and it is flat." See the §7
banner.

### Sector — `unrateable-pending` (all cells)

The bar's sector test requires per-sector statistics the engine does not yet emit — `sectorStats` has
**no clustered SE, no trimmed/robust edge, and no max-single-trade-share** (the concentration guard's
input). Without them the bar's sector rule (k=2.5, concentration ≤~40% of cell P&L, robust-sign
agreement) cannot run, so the **entire Sector dimension is rated `unrateable-pending-instrumentation`**
(issue #167) — the honest state, not a blocker on shipping the redesign. The §8 league table is shown
**descriptively** (the bad-print audit lens), but no sector cell is rated.

---

## 3. Gate table — for information only

> **These gates do not bind here.** Only the firewall's own block runs adjudicate (ADR 0008). The values
> below are evaluated against the **25y walk-forward spine** (a single 36/12/12 cadence anchored at
> 2000), which has *different IS-anchoring and window phasing* than the firewall's three block runs. Read
> this as "where would the spine land," never as a verdict.

| Gate (firewall) | Floor | Spine-proxy value | Read (informational) | Margin | Real firewall (EX-ATR20×SSM) |
|---|---|---|---|---|---|
| G1 — CAGR | ≥ 25% | OOS **12.9%** | fail (informational) | −12.1 pp | in-mkt 9.6% (fail) |
| G9 — Sharpe | ≥ 0.5 | OOS **0.49** | marginal fail | −0.01 | — |
| C1c — in-mkt Calmar | ≥ 0.5 | OOS **0.34** | fail | −0.16 | in-mkt 0.42 (fail) |
| G15 — absolute Calmar | ≥ 1.5 | OOS **0.34** | fail (well below) | −1.16 | — |
| SPY baseline (ADR 0013) | strat Calmar ≥ SPY | **0.34 vs 0.56** | FAIL | −0.22 | FAIL |
| C2 — aggregate maxDD | ≤ 25% | spine **38.1%** | fail | +13.1 pp | 42.3% (fail) |
| C3 — worst-window DD | ≤ 20% | proxy **26.2%** | fail | +6.2 pp | 22.6% (fail) |
| C5 — edge CoV | ≤ 1.5 | proxy **2.00** | fail | +0.50 | in-mkt 1.86 (fail) |
| C7 — negative participating windows | ≤ 1 | **9 / 22** | fail | +8 | 8 (fail) |
| WFE | (no OOS collapse) | **1.055** | OOS ≈ IS (informational) | — | — |
| Win rate | informational | **32.5%** | — | — | — |

The spine reproduces the firewall's failure *shape*: sub-floor return, sub-1.5 Calmar, SPY-baseline
FAIL, a heavy negative-window count — consistent with the recorded all-weather death.

### Per-window OOS spine (the 22 windows)

| OOS yr | CAGR % | edge/trade | trades | win % | maxDD % |
|---|---|---|---|---|---|
| 2003 | 86.0 | 30.27 | 31 | 51.6 | 6.5 |
| 2004 | 41.4 | 11.88 | 51 | 35.3 | 9.6 |
| 2005 | 37.1 | 5.63 | 44 | 27.3 | 26.2 |
| 2006 | 8.7 | 7.14 | 31 | 41.9 | 19.1 |
| 2007 | **−3.4** | 0.81 | 41 | 39.0 | 15.2 |
| 2008 | **−76.0** | −4.89 | 1 | 0.0 | 0.8 |
| 2009 | 16.2 | 2.49 | 31 | 29.0 | 11.0 |
| 2010 | 34.9 | 7.07 | 39 | 30.8 | 14.7 |
| 2011 | **−4.6** | −2.38 | 33 | 18.2 | 15.4 |
| 2012 | 10.4 | 2.42 | 40 | 35.0 | 12.2 |
| 2013 | 3.2 | 7.41 | 41 | 36.6 | 7.6 |
| 2014 | **−8.9** | −0.96 | 33 | 33.3 | 10.9 |
| 2015 | **−16.3** | −1.02 | 38 | 26.3 | 13.8 |
| 2016 | **−11.2** | −0.87 | 53 | 20.8 | 14.7 |
| 2017 | 6.6 | 2.39 | 58 | 34.5 | 11.8 |
| 2018 | 9.6 | 2.95 | 35 | 34.3 | 15.9 |
| 2019 | 10.7 | 1.23 | 38 | 42.1 | 12.8 |
| 2020 | 55.6 | 20.10 | 31 | 51.6 | 12.2 |
| 2021 | **−2.5** | 0.15 | 51 | 29.4 | 16.1 |
| 2022 | **−7.6** | −4.02 | 10 | 10.0 | 9.2 |
| 2023 | **−13.0** | −1.33 | 37 | 13.5 | 18.1 |
| 2024 | 7.4 | 0.81 | 39 | 35.9 | 10.1 |

Aggregate OOS: CAGR 12.9%, edge **4.29/trade** (spine `aggregateOosEdge`; the *continuous*-run edge is
3.66 — §4), 806 trades, win 32.5%, WFE 1.055, maxDD 38.1%, Sharpe 0.49, Calmar 0.34. **9/22 negative
windows** (2007, 2008, 2011, 2014, 2015, 2016, 2021, 2022, 2023). Per-window edges are all sane — no
monster window (2005 = 5.63, not the thousands a contaminated bar would print): the clean spine carries
**no bad-print artifact.** 2008 (1 trade, −76% on that single position) shows `spyTrendUp` flattening
the book in outright crisis — the documented "stands aside only in outright crisis" behaviour.

### Block-range proxy slices — proxies, NOT the firewall's block verdicts

> The spine's windows are bucketed by OOS-start year into the firewall's block date ranges. **These are
> proxies** — different IS-anchoring and window phasing than the firewall's dedicated Block A/B/C runs.
> The "mean per-window CAGR" is an arithmetic bucket average, **not a compounded block return.**

| Block range (proxy) | windows | neg | mean per-window CAGR | mean per-window edge | trades | worst-win DD | Real firewall (block run) |
|---|---|---|---|---|---|---|---|
| A — 2000-2014 | 11 | 3 | 14.0% | 6.17 | 383 | 26.2% | in-mkt CAGR 16.9% |
| B — 2014-2021H1 | 7 | 3 | 6.6% | 3.40 | 286 | 15.9% | in-mkt CAGR 20.8%, **0 neg windows** |
| C — 2021-2025 ⚠ | 4 | 3 | −4.0% | −1.10 | 137 | 18.1% | decorative — see §9 C-span stamp |

**Read the Block-B divergence as the warning it is:** the proxy slice shows **3 negative windows**
(2014/15/16) where the *real* firewall Block B shows **0** and a 20.8% in-market CAGR. That gap is
exactly what "proxy ≠ block verdict" means — the firewall's Block B is its own walk-forward with
block-specific IS-anchoring and phasing, and the load-bearing "0 negative windows / real 2020 +56.5%
recovery alpha" finding lives there. **Do not treat the proxy slice as overturning the firewall record.**

> ⚠ **operator-eyeballed-C: this family's firewall Block C verdict is decorative from here (ADR 0022).**

---

## 4. The real equity path (continuous 25y run)

From `239232fb…` — the one artifact showing the **un-stitched** drawdown path an account would have
lived through (the WF stitch omits IS-window drawdowns, ADR 0005).

| Metric | Continuous (real path) | Stitched spine (OOS-only) |
|---|---|---|
| CAGR | **14.0%** | 12.9% |
| Edge / trade | 3.66 | 4.29 |
| Trades | 1043 | 806 |
| Win rate | 33.4% | 32.5% |
| Sharpe | 0.66 | 0.49 |
| Calmar | 0.32 | 0.34 |
| **Max drawdown** | **~43.9%** (CAGR/Calmar) | 38.1% (stitch understates) |
| Profit factor | 0.36 | — |
| Avg win / avg loss | +21.4% / −5.2% | — |

**Drawdown the account actually lived through.** The deepest episodes:

| Peak | Trough | Recovery | Decline days | Recovery days |
|---|---|---|---|---|
| **2021-02-17** | **2024-08-12** | **not recovered** ⚠ | 1272 | — (open at run end) |
| 2006-01-27 | 2006-05-23 | 2010-11-03 | 116 | 1625 |
| 2015-03-20 | 2016-04-05 | 2017-10-03 | 382 | 546 |
| 2005-07-20 | 2005-08-30 | 2005-12-27 | 41 | 119 |
| 2004-03-03 | 2004-07-23 | 2004-11-26 | 142 | 126 |
| 2011-02-11 | 2011-12-14 | 2012-10-01 | 306 | 292 |

The headline ~44% DD is **not** a 2008 event — `spyTrendUp` flattened the book through the GFC (2008 =
1-2 trades). The real maxDD is a **3.5-year, still-unrecovered slide from a Feb-2021 peak to an Aug-2024
trough** — squarely the narrow-leadership-chop regime where this premise participates and bleeds. The
stitched spine's 38.1% materially **understates** this (the stitch drops the IS-window portions of the
slide). An operator would have sat in a deep, multi-year underwater curve across the entire C-span.

**Deployment / time-in-cash.** Avg holding ~46 days (stop-outs ~14d / 50-EMA exits ~59d), ~40 trades/yr
→ the book runs **~7 of its 10 slots filled on average**, flattening to near-cash only in outright index
downtrends (2001: 1 trade, 2008: 2). Idle cash is credited per ADR 0016. **This deployment profile is
the mechanical signature of the broad-`adverse` rating**: the book does *not* sit out narrow-leadership
chop — it stays materially deployed and loses. (The equity artifact carries only date + cumulative
return, so a precise dollar-idle fraction is not surfaced; the figure is slot-occupancy from holding
period × cadence.)

Beta to SPY is low (correlation **0.20**, beta **0.21**) — consistent with *participate-and-lose, not
[[beta-delivery]]*; the loss is cross-sectional, not index exposure.

> ⚠ **operator-eyeballed-C: this family's firewall Block C verdict is decorative from here (ADR 0022).**

---

## 5. Path risk (Monte Carlo)

> **Computed on the continuous run's trades (IS-inclusive) — deviation from ADR 0022 pending #161.** The
> stitched-OOS prescription is unimplementable until the engine gap closes; this MC re-orders the
> continuous trade sequence (10,000 trade-shuffle orderings, sized).

**Use the drawdown distribution ONLY. The return envelope is degenerate and is not cited** — risk-based
full-reinvestment compounding inflates it absurdly (mean terminal return ≈ 123,000% vs the realized
2,593%; the p5/p95 band collapses to ~123k–124k). **No MC return number is cited here.**

Drawdown distribution:

| | maxDD |
|---|---|
| mean | 33.5% |
| median | 32.5% |
| p5 / p25 / p50 / p75 / p95 | 24.7% / 28.9% / 32.5% / 37.2% / **45.3%** |

| Exceedance | Probability | E[DD \| exceeded] |
|---|---|---|
| DD > 20% | **~100%** (99.97%) | 33.5% |
| DD > 25% | **94.2%** | 34.1% |
| DD > 30% | **67.3%** | 36.6% |
| DD > 40% | **14.8%** | 44.6% |

**The realized ~46% maxDD sat near the bad tail** — just above p95 (45.3%), among the worst ~15% of
orderings (those exceeding 40%). So the headline DD is **not a median outcome — it is an
unlucky-but-not-extreme draw**: a >20% drawdown is essentially certain, a >30% drawdown more likely than
not, and the path the account walked was near the bad-tail edge. Win rate and per-trade edge are
invariant under reshuffling (single realized trade set), so the MC speaks only to *path/ordering* risk,
not edge confidence.

> ⚠ **operator-eyeballed-C: this family's firewall Block C verdict is decorative from here (ADR 0022).**

---

## 6. Search luck (deflated-Sharpe flag)

| | |
|---|---|
| Deflated Sharpe | **0.841** |
| Probabilistic Sharpe | 0.841 |
| Flag | **AMBER** (< 0.95) |
| nEff | 1 |
| Expected max Sharpe | 0.0 (no deflation applied) |
| nObs | 1043 |

**Itemized lineage list (always published):**

1. `assessment:minervini-vcp-breakout`

**Hidden N is the sin; the list is always published — and this list understates the true search
burden.** `nEff = 1` means *no* deflation was applied (expectedMaxSharpe 0), but minervini's real search
history — the 2026-06-03 firewall run, the ranker sweep (`TrailingReturn` lost to Random), and **three**
pre-registered gate-fix attempts — **predates the dossier register and is therefore absent from nEff.**
The AMBER PSR (84.1% probability the true Sharpe exceeds 0) is an **upper bound on confidence**: the
genuine multiple-testing count is materially higher than 1. Additional caveats: skew/kurtosis defaulted
to Gaussian; the observed per-trade Sharpe feeding the calc is small (~0.031).

This assessment run itself counts as a deflated-Sharpe trial going forward (ADR 0022 coupling 1), and it
attaches the permanent **operator-eyeballed-C** annotation to the premise family (coupling 2).

---

## 7. Regime view (the deployment centerpiece)

Per-regime decomposition of the continuous run's 1043 trades, bucketed by the **published**
(dwell-smoothed) regime label at entry. Edge is per-trade %, net of cost; SE is entry-month/date-clustered.
Insufficient-N floor = 30 trades.

**Raw-vs-published divergence: 349 / 1043 trades** entered on a day whose instantaneous raw label
differed from the dwell-debounced published label — a reminder the labels are dwell-smoothed, not
instantaneous.

### Authoritative rows (gateable grades) — these feed the §2 regime ratings

| Published regime | N | edge/trade | SE | t = edge/SE | trust grade (ADR 0024) | §2 rating |
|---|---|---|---|---|---|---|
| **THRUST** | 169 | **+7.45** | 3.80 | **1.96** | **B — precision-only, gateable** | **`neutral`** |
| **CRISIS** | 68 | **−3.05** | 1.20 | **−2.54** | **A — authoritative, gateable** | **`adverse`** |

- **CRISIS (Grade A — authoritative).** Edge −3.05/trade over 68 trades, t = −2.54. CRISIS is a
  *confirmation* of "in or recovering from a ≥20% drawdown / sustained washout" — **never an early
  warning; it lags topping phases.** Read honestly: **this long breakout premise loses in confirmed
  crisis** — it does not defend, it bleeds. Citable (Grade A, N>30), premise-consistent.
- **THRUST (Grade B — precision-only).** Edge +7.45/trade over 169 trades, **t = 1.96 — a hair under the
  k=2 bar** → the ADR 0025 bar returns **`neutral`, not favourable** (see §2). Corroborates the
  long-documented "edge is real in broad-thrust tape" finding on the clean trade population. **Carries
  the drawdown-recovery blind spot:** THRUST is structurally suppressed ~12 months after any crash
  because the dd-CRISIS leg takes precedence (2009-Q2/Q3 published CRISIS at 0% THRUST) — an accepted
  trade-off, not a tunable defect. Deploy-in-uptrend intent belongs to the leadership-gap regime (ADR
  0010), not THRUST. The +7.45 is **DESCRIPTIVE-ONLY.**

### GRIND / NARROW / CHOP rows — `unrateable`

> *Labels below CRISIS/THRUST are not separable by the read-out's axes; treat as a single
> uptrend/unclassified bucket. Do not cite per-bucket edge for these rows as evidence for or against a
> strategy.*

| Published regime | N | edge/trade (uncitable) | SE | trust grade |
|---|---|---|---|---|
| GRIND | 92 | +4.44 | 2.49 | D — descriptive-only |
| NARROW | 158 | +2.05 | 1.94 | D — descriptive-only |
| CHOP | 556 | +3.65 | 1.00 | D — descriptive-only |

These rows are **well-populated but mislabeled, not thin** — the insufficient-N floor is satisfied, yet
the edge is uncitable because the three cheap daily axes cannot resolve these labels (ADR 0024 Grade D).
Printed for completeness under the banner; **they are not evidence**, and §2 rates them `unrateable`.

### Regime × sector drill-down (readable cells only)

Under the gateable rows, the only sector cell clearing the 30-trade floor is **THRUST × XLF** (35 trades,
edge +3.53). It sits *below* the THRUST row aggregate (+7.45), and every other THRUST sector cell is
insufficient — so the (already-only-`neutral`) THRUST reading is **not** a disguised financials tilt (no
single sector ≥30 trades carries it). Readable cells under CHOP exist (XLF +4.47, XLI −1.59, XLV +5.14,
XLK +2.35, XLY +5.12, XLE +3.05, XLB +6.58, XLC +4.04) but inherit the row's D-grade — uncitable. (A
strategy-blind regime × sector return matrix is also on file, descriptive of how sector ETFs behave per
regime independent of this strategy, governed by the same D-grade banner for GRIND/NARROW/CHOP.)

### Current-regime line

As of **2026-06-10** the read-out publishes **CHOP** (raw = published; axes: breadth 40.0, slope −1.88,
gap +0.0199 POS, vol 0.14, washout false, dd-from-252-high −4.5%, direction −1.73% inside the dead-band).

**Today is neither the THRUST it is borderline-positive in, nor the CRISIS it loses in.** Per ADR 0024,
CHOP collapses into the de-emphasized **"uptrend — fine-grain label unreliable"** state. **No per-bucket
edge is attached** to today's regime (the collapsed state is uncitable). For the operator: CHOP here means
*"unclassified," not "the tape is choppy"* — it is the **residual** label. Mechanically today fails every
defined leg: not CRISIS (no washout, dd −4.5% > −20%), not THRUST (breadth 40 < 50, slope not rising, gap
POS not NEG), not NARROW (breadth 40 not weak ≤35, slope −1.88 above the −3 FALLING cut), not GRIND (gap
POS, not NEUTRAL) → falls through to CHOP.

> **Descriptive only. Adding a regime gate because of this table is regime-overfitting (ARS); it informs
> deployment, never design.**

> ⚠ **operator-eyeballed-C: this family's firewall Block C verdict is decorative from here (ADR 0022).**

---

## 8. Per-sector performance (unconditional) — descriptive; dimension rated `unrateable-pending`

From the continuous run's `sectorStats` — the **marginal** sector view across *all* regimes (distinct
from §7's regime×sector cells, which condition sector on regime). League by trade count. The 30-trade
insufficient-N floor is met by every sector. Sector totals sum to 1043 (full trade set).

> **The Sector applicability dimension is `unrateable-pending-instrumentation` (§2, issue #167).** The
> bar's sector test needs **clustered SE, a trimmed/robust edge, and the max-single-trade share** that
> `sectorStats` does not emit. The table below is therefore an **observational / audit lens only** — no
> cell is rated, and the raw `edge` column must not be read as a directional signal (it is the raw mean,
> exactly what the bar refuses to trust on a per-cell basis).

| Sector | N | win % | edge/trade (raw) | avg win % | avg loss % | total profit % | maxDD % | edge consistency |
|---|---|---|---|---|---|---|---|---|
| XLF (financials) | 171 | 33.9 | 3.75 | 18.7 | 4.0 | 640.7 | 69.5 | 46.6 |
| XLK (tech) | 139 | 25.9 | 3.12 | 29.7 | 6.2 | 433.9 | 145.8 | 24.0 |
| XLV (health) | 127 | 43.3 | 4.52 | 18.4 | 6.1 | 574.2 | 86.2 | 56.4 |
| XLI (industrials) | 127 | 34.6 | 2.39 | 17.2 | 5.5 | 303.4 | 137.3 | 40.4 |
| XLY (cons. disc.) | 125 | 28.0 | 2.81 | 25.2 | 5.9 | 351.4 | 158.6 | 22.6 |
| XLB (materials) | 82 | 39.0 | 5.84 | 24.2 | 5.9 | 479.2 | 49.6 | 45.0 |
| XLC (comms) | 70 | 30.0 | 1.25 | 15.0 | 4.7 | 87.8 | 134.1 | 25.7 |
| XLE (energy) | 70 | 28.6 | 1.18 | 17.1 | 5.2 | 82.5 | 61.0 | 27.4 |
| XLP (staples) | 52 | 30.8 | 7.94 | 35.8 | 4.4 | 413.0 | 43.7 | 29.6 |
| XLRE (real estate) | 48 | 41.7 | 3.61 | 13.8 | 3.7 | 173.3 | 22.8 | 41.5 |
| XLU (utilities) | 32 | 34.4 | 8.51 | 32.5 | 4.0 | 272.3 | 21.3 | 32.4 |

**Where the strategy concentrated:** the book is **broad, not narrow** — its three largest buckets are
XLF (171), XLK (139), XLV (127), and the top five sectors each carry >120 trades. No single sector
dominates; the breakout stack fires across the whole cyclical/defensive spectrum. The thinnest
participation is XLU (32) and XLRE (48) — both above the floor. Note the highest *raw* edges sit in the
thinnest cells (XLU 8.51 on 32 trades, XLP 7.94 on 52) — exactly the tail-carried-mean pattern the
missing trimmed-edge/concentration guard (#167) exists to neutralize, and the reason no cell is rated.

**Clean-data confirmation (the dev-contamination audit).** Every sector row is positive on total profit
and every per-trade edge is in the **single digits** (max 8.51, XLU). There is **no monster row** — the
dev run's ~24× edge inflation would have shown here as a multi-thousand-percent sector cell. The
per-sector league is precisely the lens that exposes the dev artifact: a blended headline averages a
$65k VPI / $26.4k LAF print across hundreds of trades and hides it, whereas the sector (and per-stock)
breakdown localizes it to two symbols. Top per-stock profits are all plausible multi-baggers — CALM
+389%, TGISQ +248%, ALVU +214% — none contamination. **This view is the durable audit tool the
supersession rests on** (and the instrumentation #167 would add — clustered SE + trimmed edge +
max-single-trade-share — is the same audit, formalized into the rating bar).

> **Descriptive only. Pruning to the winning sectors after seeing this table is sector-overfitting; it
> informs understanding, never design.**

> ⚠ **operator-eyeballed-C: this family's firewall Block C verdict is decorative from here (ADR 0022).**

---

## 9. C-span contamination — standing disclosure

Per ADR 0022/0007, from this family's first assessment onward the **firewall's Block C (2021-2025)
verdict is decorative.** Every section above that shows 2021–2025 numbers (§2 broad evidence + the
THRUST/CRISIS buckets, §3 Block-C proxy + 2021-2024 per-window rows, §4 the 2021→2024 deepest drawdown,
§5 the IS-inclusive MC, §7 regime decomposition + current-regime line, §8 2021-2025 sector edges) carries
the operator-eyeballed-C stamp. The leak is about the **tape**, not the block label — the spine's 2021-25
windows are the same leak surface. The deflated-Sharpe readout (§6) surfaces this annotation permanently
for the premise family.

---

## 10. Decision support

The operator records exactly **one** of five decisions, and (ADR 0025) **names the dimension it targets.**
The ratings (§2) are recorded as a **separate `RATINGS` ledger event** from the `DECISION`. This report
may recommend; **the operator decides and the skill records it** — this report records nothing.

### The §2 ratings, in one line

- **Broad → `adverse`.** All-weather application is not viable (SPY-baseline FAIL, sub-floor, 44%
  unrecovered DD). The firewall's death record stands and is the only road to TRADABLE.
- **Regime THRUST → `neutral`** (borderline-positive, t ≈ 1.96, descriptive-only, recovery-blind). The
  named confirm-path is a within-THRUST conditional null on a *fresh* THRUST-scoped candidate.
- **Regime CRISIS → `adverse`** (t ≈ −2.54, premise-consistent, lags-topping/survivorship caveat).
- **Regime GRIND/NARROW/CHOP → `unrateable`** (Grade-D, below resolving power).
- **Sector → `unrateable-pending`** (instrumentation #167).

### The five recorded decisions

| Decision | Evidence FOR | Evidence AGAINST |
|---|---|---|
| **shelve** *(operator's lean — dimension `broad`)* | Park the all-weather config: it is broad-`adverse` and firewall-DEAD, but the building block is **known-real and Block-B-proven** (0 neg windows, 20.8% in-mkt CAGR, 2020 +56.5% recovery alpha), and the THRUST bucket (+7.45, `neutral`) keeps the broad-thrust finding alive. Parking preserves the capital without spending compute now; the THRUST-specialist host does not exist yet (a from-scratch v3 classifier axis is a prerequisite). Promoted `NarrowingRange`/`VolumeDryUp` survive as reusable parts. | Leaves a real edge idle; does not itself advance the specialist hypothesis (that is the separate `redesign` lean below). |
| **redesign** *(future — toward a THRUST-regime specialist)* | The THRUST bucket is the kernel of a genuinely **new** candidate: a THRUST-regime specialist with a from-scratch entry premise that resolves the cross-section in thin tape, validated by a **regime-conditional firewall + within-THRUST conditional null**. Fits the operator's regime-specialist thesis (a stable of per-regime specialists routed by a classifier, this the THRUST leg). | THRUST is only **`neutral`** (t ≈ 1.96) — a hypothesis, not a conviction; the confirm-path must run first. Blocked on an unbuilt dependency: a reliable **non-CRISIS/non-THRUST regime axis** (ADR 0024 v3, from-scratch — the three cheap axes cannot resolve GRIND/NARROW/CHOP). THRUST itself carries the dd-recovery blind spot. Significant net-new design; **strict-distinct discipline** required to avoid sliding into the rescue trap. |
| **send-to-firewall** | — | This exact config is already firewall-DEAD (config_hash dead); the lineage DISTINCT gate hard-refuses re-advancing a dead hash or a G13 neighbour. Re-running learns nothing new. |
| **paper-trade** | — | Broad-`adverse` (sub-floor + SPY-FAIL + 44% unrecovered DD); paper-trading a config the firewall already killed adds C-span exposure for no decision value. |
| **deploy-at-own-risk** | — | Firewall-DEAD; deploying a participate-and-lose book through the current collapsed-uptrend (CHOP) regime invites exactly the 2021→2024 slide. Not supportable. |

### Recommendation (operator decides; not recorded here)

The dual-outcome nuance the flat ADR 0022 vocabulary used to collapse now lives in the §2 ratings, so the
single decision can be clean:

- **Lean: `shelve`, naming dimension `broad`.** Park the all-weather config — it is broad-`adverse` and
  firewall-DEAD, and the all-weather axis is not re-openable on this config. This is the terminal decision
  for *this* config.
- **Carry the THRUST hypothesis forward as a future `redesign`, NOT as a gate on this config.** The
  `neutral` THRUST reading (t ≈ 1.96) is a hypothesis worth a *fresh, distinct, THRUST-scoped candidate*
  with a within-THRUST conditional null and a regime-conditional firewall — **never** a THRUST gate bolted
  onto `81a1d38ee0a6`. Post-hoc gating to the regime the table shows winning is the **rescue-forbidden
  (ADR 0023) / [[aliased-regime-sensitivity]] trap**, and the lineage DISTINCT gate would refuse it as a
  disguised re-run. The redesign is additionally blocked on the from-scratch v3 regime axis a specialist
  *stable* needs.

> **No single-bucket decision is recorded by this report.** The operator records the `RATINGS` event (§2)
> and one `DECISION` (with its named dimension) via the skill's `record_decision(...)`.

---

## KNOWLEDGE-UPDATE

```
KNOWLEDGE-UPDATE: entity [[minervini-vcp-breakout]] — RE-RATED under ADR 0025 (2026-06-13, config_hash
81a1d38ee0a6, udgaard 1.0.93, PRD clean). Applicability ratings (descriptive, verdict-free): broad=ADVERSE
(SPY-baseline FAIL 0.34 vs 0.56, OOS CAGR 12.9%, 44% unrecovered DD, 9/22 neg windows); regime:THRUST=
NEUTRAL (edge +7.45 ± 3.80, N=169, t=1.96 — a hair UNDER the k=2 bar, so the ADR 0025 bar DOWNGRADES the
prior free-text "THRUST-favourable" headline to neutral/weak-hypothesis; recovery-blind; confirm-path =
within-THRUST null on a FRESH candidate); regime:CRISIS=ADVERSE (edge −3.05 ± 1.20, N=68, t=−2.54,
premise-consistent, lags-topping); regime:GRIND/NARROW/CHOP=UNRATEABLE (Grade-D, below resolving power);
sector=UNRATEABLE-PENDING (#167 — sectorStats lacks clustered SE / trimmed edge / max-trade-share).
Operator lean: shelve(dimension=broad) + THRUST as future redesign (NOT a gate on this config). The
all-weather firewall death is unchanged and remains the only road to TRADABLE.
```
```
KNOWLEDGE-UPDATE: concept [[strategy-assessment]] — ADR 0025 worked example: the rating bar is MORE
CONSERVATIVE than free-text prose. Minervini's THRUST bucket (+7.45 ± 3.80, t=1.96) read as "favourable"
in unstructured prose but the k=2 directional bar (|edge| ≤ k·SE: 7.45 ≤ 7.60) returns NEUTRAL — the
marginal t-stat is the brake that insists on the confirm-path (within-condition null on a fresh candidate)
before any conviction. unrateable is never collapsed into neutral: a Grade-D label and a pending-
instrumentation dimension both ship as unrateable("we cannot say"), distinct from neutral ("we looked, it
is flat"). The within-condition null stays the NAMED confirm-path, never run inside the battery (~13 fresh
backtests, multiplies DSR trials, recreates the rescue pull).
```
```
KNOWLEDGE-UPDATE: concept [[strategy-assessment]] — the Sector applicability dimension ships UNRATEABLE-
PENDING-INSTRUMENTATION until issue #167 adds per-sector clustered SE + trimmed/robust edge + max-single-
trade-share to sectorStats. The §8 league table is still emitted as a DESCRIPTIVE / bad-print audit lens,
but no cell is rated. Tell that motivates the guard: minervini's highest RAW per-cell edges sit in the
THINNEST cells (XLU 8.51 on 32 trades, XLP 7.94 on 52) — the tail-carried-mean pattern the trimmed-edge +
concentration guard exists to neutralize.
```
```
KNOWLEDGE-UPDATE: concept [[regime-read-out]] / [[participate-and-lose]] — minervini's deployment profile
is the mechanical signature of the broad-ADVERSE rating: avg ~7 of 10 slots filled, flattening to near-cash
ONLY in outright index downtrends (2001: 1 trade, 2008: 2). It does not sit out narrow-leadership chop —
the real ~44% maxDD is a 2021→2024 STILL-UNRECOVERED slide (NOT a 2008 event), confirming the loss lives in
the cross-section through chop, exactly where spyTrendUp keeps the book deployed. Low SPY beta (corr 0.20,
β 0.21) = participate-and-lose, not beta-delivery.
```
