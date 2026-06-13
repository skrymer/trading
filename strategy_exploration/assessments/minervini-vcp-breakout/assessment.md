# Strategy Assessment — Minervini VCP Breakout

| | |
|---|---|
| **Candidate** | `minervini-vcp-breakout` (EX-ATR20 × SectorStrengthMomentum) |
| **Config hash** | `81a1d38ee0a6` |
| **Report date** | 2026-06-13 |
| **Environment** | **PRD** (`:9080`), clean universe, udgaard **1.0.93** |
| **Funnel** | `/assess-strategy` — ADR 0022 non-adjudicating battery |
| **Continuous backtestId** | `239232fb-81cc-4eb8-8a48-c7805527c128` |

> This report **informs a human decision; it does not make one.** It carries no verdict. Where the
> firewall's own `REJECTED` verdict is named below, it is cited as a *referenced historical fact*, never
> issued here as an assessment outcome.

---

## 1. Framing — autopsy of a firewall-DEAD config

**This config is DEAD in the validation funnel.** It was `REJECTED` at the Component Firewall on
2026-06-03 (six binding gates; quant-confirmed, decisive — not a near-miss). The breakout-in-uptrend
entry-time-regime premise class was `DEPRECATED` after three pre-registered market-gate fixes all
failed (STRIKE 3). **This report informs a redesign, never a re-run.** The only road to TRADABLE
remains the firewall (ADR 0008 untouched); nothing here resurrects, settles, or kills.

### ⚠ This PRD re-run SUPERSEDES an invalidated 2026-06-12 dev run

A 2026-06-12 `/assess-strategy` run executed against the **dev** universe was **thrown out as
artifactual.** Dev's database carried un-caught bad prints — split-adjustment failures including
**VPI ($65k bar)** and **LAF ($26.4k bar)**; midgaard's integrity validators flagged **~166 symbols**.
The contamination:

- inflated the per-trade edge **~24×** (3.7 → **90.9**),
- lifted CAGR **~25 points** (12.9% → **37.7%**),
- made the Monte Carlo **degenerate**, and
- **spuriously PASSED the SPY baseline** (a clean-data FAIL flipped to a pass on phantom returns).

**PRD is clean.** One integrity violation surfaced — **ERX**, a leveraged ETF outside the STOCK
universe — which is irrelevant to this `assetTypes:[STOCK]` config. The clean PRD numbers below
**reproduce the firewall record** (OOS CAGR 12.9% ≈ the documented 9.6% in-market / 12.7% blended; SPY
baseline FAIL; 9 negative windows). **The dev figures were data artifacts; the PRD figures are the
strategy.** §7 (per-sector) localized the dev contamination to the two symbols a blended headline hid —
itself a durable finding (see KNOWLEDGE-UPDATE).

### Pre-flight advisories (carried verbatim)

- **No blockers.** Pre-flight passed; battery fired in full.
- **Firewall-DEAD config → autopsy framing** (not refusal). Carried.
- **Random-baseline arm DELIBERATELY SKIPPED** — the entry is a tight 10-condition breakout stack (not
  permissive-entry + ranker-selects), so the byte-identical Random-baseline beta guard does not apply;
  **operator-confirmed.**
- Entry conditions `narrowingRange` / `volumeDryUp` are already first-class promoted conditions
  (`/verify-promotion` G14-PASS, 946=946 trades, Jaccard 1.000000) — **no inline-`script` advisory
  applies** to this config.

### Config under assessment

ATR-risk sizer **1.25% risk / 2 ATR**, `maxPositions 10`, ranker **SectorStrengthMomentum**, `randomSeed 42`,
25y span 2000-01-01 → 2025-01-01, 36/12/12-month walk-forward. Entry: `spyTrendUp` AND a 50/150/200 SMA
stack with rising 200 AND within 25% of the 52-wk high AND ≥30% above the 52-wk low AND RS-percentile ≥70
AND narrowing-range AND volume dry-up AND within 1.5% of the Donchian high AND volume ≥1.3× its 20-day
average. Exit: 2-ATR stop OR close below the 50 EMA.

---

## 2. Gate table — for information only

> **These gates do not bind here.** Only the firewall's own block runs adjudicate (ADR 0008). The values
> below are evaluated against the **25y walk-forward spine** (a single 36/12/12 cadence anchored at 2000),
> which has *different IS-anchoring and window phasing* than the firewall's three block runs. Read this as
> "where would the spine land," never as a verdict.

| Gate (firewall) | Floor | Spine-proxy value | Informational read | Margin | Real firewall (EX-ATR20×SSM) |
|---|---|---|---|---|---|
| G1 — CAGR | ≥ 25% | OOS **12.9%** | below floor | −12.1 pp | in-mkt 9.6% (fail) |
| G9 — Sharpe | ≥ 0.5 | OOS **0.49** | marginal miss | −0.01 | — |
| C1c — in-mkt Calmar | ≥ 0.5 | OOS **0.34** | below | −0.16 | in-mkt 0.42 (fail) |
| G15 — absolute Calmar | ≥ 1.5 | OOS **0.34** | well below | −1.16 | — |
| SPY baseline (ADR 0013) | strat Calmar ≥ SPY | **0.34 vs 0.56** | FAIL | −0.22 | FAIL |
| C2 — aggregate maxDD | ≤ 25% | spine **38.1%** | above | +13.1 pp | 42.3% (fail) |
| C3 — worst-window DD | ≤ 20% | proxy **26.2%** | above | +6.2 pp | 22.6% (fail) |
| C5 — edge CoV | ≤ 1.5 | proxy **2.00** | above | +0.50 | in-mkt 1.86 (fail) |
| C7 — negative participating windows | ≤ 1 | **9 / 22** | above | +8 | 8 (fail) |
| WFE | (no OOS collapse) | **1.055** | OOS ≈ IS (informational) | — | — |
| Win rate | informational | **32.5%** | — | — | — |

The spine reproduces the firewall's failure *shape*: sub-floor return, sub-1.5 Calmar, SPY-baseline FAIL,
and a heavy negative-window count — consistent with the recorded `REJECTED` as an all-weather book.

### Per-window OOS spine (the 22 windows)

| OOS yr | CAGR % | edge/trade | trades | win % | maxDD % |
|---|---|---|---|---|---|
| 2003 | 86.0 | 30.27 | 31 | 51.6 | 6.5 |
| 2004 | 41.4 | 11.88 | 51 | 35.3 | 9.6 |
| 2005 | 37.1 | 5.63 | 44 | 27.3 | 26.2 |
| 2006 | 8.7 | 7.14 | 31 | 41.9 | 19.1 |
| 2007 | **−3.4** | 0.81 | 41 | 39.0 | 15.2 |
| 2008 | **−76.1** | −4.89 | 1 | 0.0 | 0.8 |
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
| 2021 | **−2.5** | 0.15 | 51 | 29.4 | 16.2 |
| 2022 | **−7.6** | −4.02 | 10 | 10.0 | 9.2 |
| 2023 | **−13.0** | −1.33 | 37 | 13.5 | 18.1 |
| 2024 | 7.3 | 0.81 | 39 | 35.9 | 10.1 |

Aggregate OOS: CAGR 12.9%, edge **4.29/trade** (the spine's `aggregateOosEdge` field; the
*continuous*-run edge is 3.66 — §3), 806 trades, win 32.5%, WFE 1.055, maxDD 38.1%, Sharpe 0.49, Calmar
0.34. **9/22 negative windows** (2007, 2008, 2011, 2014, 2015, 2016, 2021, 2022, 2023). Per-window edges
are all sane — no monster windows (2005 = 5.63, not the 1753 a contaminated bar would print): the clean
spine carries no bad-print artifact. 2008 (1 trade) and 2001/2008 in the continuous run show
`spyTrendUp` flattening the book in outright crisis — the documented "stands aside only in outright
crisis" behaviour.

### Block-range proxy slices — proxies, NOT the firewall's block verdicts

> The spine's windows are bucketed by OOS-start year into the firewall's block date ranges. **These are
> proxies** — different IS-anchoring and window phasing than the firewall's dedicated Block A/B/C runs.
> The "mean per-window CAGR" is an arithmetic bucket average, **not a compounded block return** — do not
> read it as one.

| Block range (proxy) | windows | neg | mean per-window CAGR | mean per-window edge | trades | worst-win DD | Real firewall (block run) |
|---|---|---|---|---|---|---|---|
| A — 2000-2014 | 11 | 3 | 14.0% | 6.17 | 383 | 26.2% | in-mkt CAGR 16.9% |
| B — 2014-2021H1 | 7 | 3 | 6.6% | 3.40 | 286 | 15.9% | in-mkt CAGR 20.8%, **0 neg windows** |
| C — 2021-2025 ⚠ | 4 | 3 | −4.0% | −1.10 | 137 | 18.1% | decorative — see C-span stamp |

**Read the Block-B divergence as the warning it is:** the proxy slice shows **3 negative windows**
(2014/2015/2016) where the *real* firewall Block B shows **0** and a 20.8% in-market CAGR. That gap is
exactly what "proxy ≠ block verdict" means — the firewall's Block B is its own walk-forward with
block-specific IS-anchoring and phasing, and the load-bearing "0 negative windows / real 2020 +56.5%
recovery alpha" finding lives there, not in this single-cadence spine. Do not treat the proxy slice as
overturning the firewall record.

> ⚠ **operator-eyeballed-C: this family's firewall Block C verdict is decorative from here (ADR 0022).**

---

## 3. The real equity path (continuous 25y run)

From `239232fb…` — the one artifact showing the **un-stitched** drawdown path an account would have lived
through (the WF stitch omits IS-window drawdowns, ADR 0005).

| Metric | Continuous (real path) | Stitched spine (OOS-only) |
|---|---|---|
| CAGR | **14.0%** | 12.9% |
| Edge / trade | 3.66 | 4.29 |
| Trades | 1043 | 806 |
| Win rate | 33.4% | 32.5% |
| Sharpe | 0.66 | 0.49 |
| Calmar | 0.32 | 0.34 |
| **Max drawdown** | **43.9%** | 38.1% (stitch understates) |
| Profit factor | 0.36 | — |
| Avg win / avg loss | +21.4% / −5.2% | — |

**Drawdown the account actually lived through.** The deepest episode is the load-bearing reality of this
book:

| Peak | Trough | Recovery | maxDD | Decline days | Recovery days |
|---|---|---|---|---|---|
| **2021-02-17** | **2024-08-12** | **not recovered** | **43.9%** | 1272 | — (open at run end) ⚠ |
| 2006-01-27 | 2006-05-23 | 2010-11-03 | 31.2% | 116 | 1625 |
| 2005-07-20 | 2005-08-30 | 2005-12-27 | 26.3% | 41 | 119 |
| 2004-03-03 | 2004-07-23 | 2004-11-26 | 21.7% | 142 | 126 |
| 2011-02-11 | 2011-12-14 | 2012-10-01 | 18.8% | 306 | 292 |

The headline DD is **not** a 2008 event — `spyTrendUp` flattened the book through the GFC (2008 = 1-2
trades). The real maxDD is a **3.5-year, still-unrecovered 44% slide from a Feb-2021 peak to an Aug-2024
trough** — squarely the narrow-leadership-chop regime where this premise participates and bleeds. The
stitched spine's 38.1% materially **understates** this, because the stitch drops the IS-window portions of
the slide. An operator would have sat in a deep, multi-year underwater curve across the entire C-span.

**Deployment / time-in-cash.** Avg holding ~46 days (stop-outs 13.8d / 50-EMA exits 59.1d), ~40
trades/yr → the book runs **~7 of its 10 slots filled on average**, flattening to near-cash only in
outright index downtrends (2001: 1 trade, 2008: 2). Idle cash is credited per ADR 0016. **This
deployment profile is the mechanical signature of the death**: the book does *not* sit out
narrow-leadership chop — it stays materially deployed and loses. (Note: the equity artifact carries only
date + cumulative return, so a precise dollar-idle fraction is not surfaced; the figure above is
slot-occupancy from holding period × cadence.)

Beta to SPY is low (correlation 0.20, beta 0.21) — consistent with the firewall finding that this is
**participate-and-lose, not beta-delivery**; the loss is cross-sectional, not index exposure.

> ⚠ **operator-eyeballed-C: this family's firewall Block C verdict is decorative from here (ADR 0022).**

---

## 4. Path risk (Monte Carlo)

> **Computed on the continuous run's trades (IS-inclusive) — deviation from ADR 0022 pending #161.** The
> stitched-OOS prescription is unimplementable until the engine gap closes; this MC re-orders the
> continuous trade sequence.

**Use the drawdown distribution only. The return envelope is unusable** — risk-based full-reinvestment
compounding inflates it absurdly (mean terminal return ≈ **123,000%** vs the realized **2,593%**; p5/p95
collapse to a degenerate 123k–124k band). **No MC return number is cited here.**

Drawdown distribution (10,000 trade-shuffle orderings, sized):

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

**The realized ~46% maxDD sat in the worst ~15% of orderings** (just above p95). So the headline DD is
**not a median outcome — it is an unlucky-but-not-tail draw**: a >20% drawdown is essentially certain,
a >30% drawdown is more likely than not, and the path the account actually walked was near the
bad-tail edge. Win rate and per-trade edge are invariant under reshuffling (single realized trade set),
so the MC speaks only to *path/ordering* risk, not edge confidence.

---

## 5. Search luck (deflated-Sharpe flag)

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
The AMBER PSR (84.1% probability the true Sharpe exceeds 0) should be read as an **upper bound on
confidence**: the genuine multiple-testing count is materially higher than 1. Additional caveats:
skew/kurtosis defaulted to Gaussian (0 / 3.0); the observed per-trade Sharpe feeding the calc is small
(0.031).

This assessment run itself counts as a deflated-Sharpe trial going forward (ADR 0022 coupling 1), and it
attaches the permanent **operator-eyeballed-C** annotation to the premise family (coupling 2).

---

## 6. Regime view (the deployment centerpiece)

Per-regime decomposition of the continuous run's 1043 trades, bucketed by the **published** (dwell-smoothed)
regime label at entry. Edge is per-trade %, net of cost; SE is entry-month/date-clustered. Insufficient-N
floor = 30 trades.

**Raw-vs-published divergence: 349 / 1043 trades** entered on a day whose instantaneous raw label differed
from the dwell-debounced published label — a reminder the labels are dwell-smoothed, not instantaneous.

### Authoritative rows (gateable grades)

| Published regime | N | edge/trade | SE | trust grade (ADR 0024) |
|---|---|---|---|---|
| **THRUST** | 169 | **+7.45** | 3.80 | **B — precision-only, gateable** |
| **CRISIS** | 68 | **−3.05** | 1.20 | **A — authoritative, gateable** |

- **CRISIS (Grade A — authoritative).** Edge **−3.05/trade** over 68 trades. CRISIS is a *confirmation*
  of "in or recovering from a ≥20% drawdown / sustained washout" — **never an early warning; it lags
  topping phases.** Read honestly: **this long breakout premise loses in confirmed crisis** — it does
  not defend, it bleeds. This is citable (Grade A, N>30).
- **THRUST (Grade B — precision-only).** Edge **+7.45/trade** over 169 trades — the strongest bucket, and
  it corroborates the long-documented "edge is real in broad-thrust tape" finding on the clean trade
  population. **Carries the drawdown-recovery blind spot:** THRUST is structurally suppressed for ~12
  months after any crash because the dd-CRISIS leg takes precedence (2009-Q2/Q3 published CRISIS at 0%
  THRUST) — an accepted trade-off, not a tunable defect. Deploy-in-uptrend intent belongs to the
  leadership-gap regime (ADR 0010), not THRUST. The +7.45 is **DESCRIPTIVE-ONLY** (see the standing
  warning and §9 guardrails).

### GRIND / NARROW / CHOP rows

> *Labels below CRISIS/THRUST are not separable by the read-out's axes; treat as a single
> uptrend/unclassified bucket. Do not cite per-bucket edge for these rows as evidence for or against a
> strategy.*

| Published regime | N | edge/trade (uncitable) | SE | trust grade |
|---|---|---|---|---|
| GRIND | 92 | +4.44 | 2.49 | D — descriptive-only |
| NARROW | 158 | +2.05 | 1.94 | D — descriptive-only |
| CHOP | 556 | +3.65 | 1.00 | D — descriptive-only |

These rows are **well-populated but mislabeled, not thin** — the insufficient-N floor is satisfied, yet
the edge remains uncitable because the three cheap daily axes cannot resolve these labels (ADR 0024
Grade D). The numbers are printed for completeness under the banner; **they are not evidence.**

### Regime × sector drill-down (readable cells only)

Under the gateable rows, the only sector cell clearing the 30-trade floor is **THRUST × XLF** (35 trades,
edge +3.53). It sits *below* the THRUST row aggregate (+7.45), and every other THRUST sector cell is
insufficient — so the THRUST edge is **not** a disguised financials tilt (no single sector ≥30 trades
carries it). Readable cells under CHOP exist (XLF/XLI/XLV/XLK/XLY/XLE/XLB/XLC) but inherit the row's
D-grade — uncitable. (A strategy-blind regime × sector return matrix is also on file; it is descriptive of
how sector ETFs behave per regime, independent of this strategy, and is governed by the same D-grade
banner for GRIND/NARROW/CHOP.)

### Current-regime line

As of **2026-06-10** the read-out publishes **CHOP** (raw = published; axes: breadth 40.0, slope −1.88,
gap +0.0199 POS, vol 0.14, washout false, dd-from-252-high −4.5%, direction −1.73% inside the ±2%
dead-band).

**Today is neither THRUST nor CRISIS.** Per ADR 0024, CHOP collapses into the de-emphasized
**"uptrend — fine-grain label unreliable"** state. **No per-bucket edge is attached** to today's regime
(the collapsed state is uncitable). For the operator: CHOP here means *"unclassified," not "the tape is
choppy"* — it is the **residual** label. Mechanically today fails every defined leg: not CRISIS (no
washout, dd −4.5% > −20%), not THRUST (breadth 40 < 50 + slope not rising + gap POS not NEG), not NARROW
(breadth 40 is not weak ≤35 and slope −1.88 is above the −3 FALLING cutoff), not GRIND (gap is POS, not
NEUTRAL) → it falls through to CHOP.

> **Descriptive only. Adding a regime gate because of this table is regime-overfitting (ARS); it informs
> deployment, never design.**

> ⚠ **operator-eyeballed-C: this family's firewall Block C verdict is decorative from here (ADR 0022).**

---

## 7. Per-sector performance (unconditional)

From the continuous run's `sectorStats` — the **marginal** sector view across *all* regimes (distinct
from §6's regime×sector cells, which condition sector on regime). League by trade count. Insufficient-N
floor = 30 trades (none tripped — every sector cleared it). Sector totals sum to 1043 (full trade set).

| Sector | N | win % | edge/trade | avg win % | avg loss % | total profit % | maxDD % | edge consistency |
|---|---|---|---|---|---|---|---|---|
| XLF (financials) | 171 | 33.9 | 3.75 | 18.8 | 4.0 | 640.7 | 69.5 | 46.6 |
| XLK (tech) | 139 | 25.9 | 3.12 | 29.7 | 6.2 | 433.9 | 145.8 | 24.0 |
| XLV (health) | 127 | 43.3 | 4.52 | 18.4 | 6.1 | 574.3 | 86.3 | 56.4 |
| XLI (industrials) | 127 | 34.6 | 2.39 | 17.2 | 5.5 | 303.4 | 137.3 | 40.4 |
| XLY (cons. disc.) | 125 | 28.0 | 2.81 | 25.2 | 5.9 | 351.4 | 158.7 | 22.6 |
| XLB (materials) | 82 | 39.0 | 5.84 | 24.2 | 5.9 | 479.2 | 49.6 | 45.0 |
| XLC (comms) | 70 | 30.0 | 1.25 | 15.0 | 4.7 | 87.8 | 134.1 | 25.7 |
| XLE (energy) | 70 | 28.6 | 1.18 | 17.1 | 5.2 | 82.5 | 61.0 | 27.4 |
| XLP (staples) | 52 | 30.8 | 7.94 | 35.8 | 4.5 | 413.1 | 43.7 | 29.6 |
| XLRE (real estate) | 48 | 41.7 | 3.61 | 13.8 | 3.7 | 173.3 | 22.8 | 41.5 |
| XLU (utilities) | 32 | 34.4 | 8.51 | 32.5 | 4.0 | 272.3 | 21.3 | 32.4 |

**Where the strategy concentrated:** the book is **broad, not narrow** — its three largest buckets are
XLF (171), XLK (139), XLV (127), and the top five sectors each carry >120 trades. No single sector
dominates; the breakout stack fires across the whole cyclical/defensive spectrum. The thinnest
participation is XLU (32) and XLRE (48) — both still above the floor.

**Clean-data confirmation (the dev-contamination autopsy).** Every sector row is positive on total
profit and every per-trade edge is in the **single digits** (max 8.51, XLU). There is **no monster row** —
the dev run's ~24× edge inflation would have shown here as a multi-thousand-percent sector cell. The
per-sector league is precisely the lens that exposes the dev artifact: a blended headline averages a
$65k VPI / $26.4k LAF print across hundreds of trades and hides it, whereas the sector (and per-stock)
breakdown localizes it to two symbols. Top per-stock profits are all plausible multi-baggers — CALM
+389%, TGISQ +248%, ALVU +214%, AFOP +187%, PENN +175% — none contamination. **This view is the durable
audit tool the supersession rests on.**

> **Descriptive only. Pruning to the winning sectors after seeing this table is sector-overfitting; it
> informs understanding, never design.**

> ⚠ **operator-eyeballed-C: this family's firewall Block C verdict is decorative from here (ADR 0022).**

---

## 8. C-span contamination — standing disclosure

Per ADR 0022/0007, from this family's first assessment onward the **firewall's Block C (2021-2025) verdict
is decorative.** Every section above that shows 2021–2025 numbers (§2 Block-C proxy + the 2021-2024
per-window rows, §3 the 2021→2024 deepest drawdown, §6 regime decomposition + current-regime line, §7
2025 sector edges) carries the operator-eyeballed-C stamp. The leak is about the **tape**, not the block
label — the spine's 2021-25 windows are the same leak surface. The deflated-Sharpe readout (§5) surfaces
this annotation permanently for the premise family.

---

## 9. Decision support

The operator records exactly one of five decisions. This report recommends one with reasoning; **the
operator decides and the skill records it** (this report does not record a decision).

### Two honest readings of the same headline

**(a) As an all-weather book — not deployable.** The clean PRD numbers reproduce the firewall record:
sub-floor CAGR (12.9% OOS / 14.0% continuous, ≪ 25%), Calmar 0.32-0.34 (≪ 1.5), SPY-baseline FAIL on the
stitched comparison, 9/22 negative windows, a still-unrecovered 44% drawdown spanning 2021→2024. The
**firewall `REJECTED` (2026-06-03) stands**; the premise participates and loses in narrow-leadership chop,
and a market-level gate cannot rescue a cross-sectional loss (mechanistically proven —
[[r1-leadership-gap-breakout]]).

**(b) As a THRUST-regime building block — a hypothesis worth preserving.** The clean THRUST-conditional
edge (**+7.45/trade**, N=169, Grade B) corroborates the long-documented "real in broad-thrust" finding on
the same trade population, and CRISIS −3.05 confirms the inverse. This fits the operator's
**regime-specialist thesis**: an all-weather strategy is not attainable; the goal is a *stable of
per-regime specialists routed by a classifier*, with this candidate the **THRUST leg**.

> **CRITICAL GUARDRAILS.** (1) The +7.45 is **DESCRIPTIVE-ONLY.** (2) Confirming a THRUST specialist
> requires a **regime-conditional firewall validation** with a **within-THRUST conditional null** (random
> entries drawn from the comparable THRUST population at matched cadence, per the
> conditional-within-regime-null rule) — never by **gating this config to THRUST after seeing this
> table.** Post-hoc gating to the regime the table shows winning is the **rescue-forbidden (ADR 0023) /
> [[aliased-regime-sensitivity]] trap**, and the lineage DISTINCT gate would refuse it as a disguised
> re-run. (3) **Open dependency:** routing a specialist stable requires a reliable proxy for the
> non-CRISIS/non-THRUST regimes, which ADR 0024 found the three cheap daily axes **cannot** resolve
> (GRIND/NARROW/CHOP are Grade D). A THRUST specialist that must *know it is in THRUST* therefore needs a
> **from-scratch v3 classifier axis** (cross-sectional dispersion / sector participation / vol
> term-structure) — and THRUST itself carries the dd-recovery blind spot (~12-month post-crash
> suppression). The specialist is a **future NEW candidate**, not a rescue of this one.

### The five recorded decisions

| Decision | Evidence FOR | Evidence AGAINST |
|---|---|---|
| **redesign** | The THRUST edge (+7.45) is the kernel of a genuinely new candidate — a THRUST-regime specialist with a from-scratch entry premise that resolves the cross-section in thin tape, validated by a regime-conditional firewall + within-THRUST null. Promoted `NarrowingRange`/`VolumeDryUp` conditions survive as reusable parts. | Blocked on an unbuilt dependency: a reliable non-CRISIS/non-THRUST regime axis (ADR 0024 v3, from-scratch). Significant net-new design + pre-registration before any value. Risk of sliding into the rescue trap if not held strictly distinct. |
| **shelve** | The building block is **known-real and Block-B-proven** (0 neg windows, 20.8% in-mkt CAGR, 2020 +56.5% recovery alpha); the clean THRUST +7.45 re-confirms it. Parking it preserves the capital without spending compute now, and the THRUST-specialist host doesn't exist yet (the v3 classifier axis is a prerequisite). The regime-conditional portfolio program that would host it is itself abandoned. | Leaves a real edge idle. Does not advance the specialist hypothesis. |
| **send-to-firewall** | — | This exact config is already firewall-`REJECTED` (config_hash dead); the lineage gate hard-refuses re-advancing a dead hash or a G13 neighbour. Re-running learns nothing new. |
| **paper-trade** | — | Sub-floor + SPY-FAIL + 44% unrecovered DD as an all-weather book; paper-trading a config the firewall already rejected adds C-span exposure for no decision value. |
| **deploy-at-own-risk** | — | Firewall-`REJECTED`; deploying a participate-and-lose book through the current (collapsed-uptrend / CHOP) regime invites exactly the 2021→2024 slide. Not supportable. |

### Recommendation (operator decides)

**This config does not reduce to one decision — read it on two axes** (a single `shelve` bucket collapses
them and is the wrong output shape — see the note below):

- **Broad / all-weather application — not viable.** Sub-floor (OOS CAGR 12.9%), SPY-baseline FAIL, 44%
  unrecovered DD, 9/22 negative windows; the firewall-`REJECTED` record stands and is the only road to
  TRADABLE. The all-weather config is parked.
- **Regime applicability — THRUST-favourable (hypothesis), CRISIS-adverse.** The clean THRUST edge (+7.45)
  re-confirms the Block-B "real in broad-thrust" finding. The forward path is a **`redesign`**: a *fresh*
  THRUST-regime-specialist candidate validated by a regime-conditional firewall + within-THRUST null —
  **never** a gating of this config to THRUST (rescue-forbidden / [[aliased-regime-sensitivity]]). It is
  blocked on an unbuilt dependency: a from-scratch v3 regime axis that resolves the non-CRISIS/non-THRUST
  states a specialist *stable* needs.

> **Output-structure note (pending design).** The five-bucket terminal decision cannot express "park the
> all-weather config **and** pursue the THRUST specialist" — it forces a lossy `shelve`. The
> assessment-analyst should emit a **Broad-application rating** + a **Regime-applicability rating** (the
> per-regime axis honest only for the gateable CRISIS/THRUST grades; GRIND/NARROW/CHOP stay unrateable
> until a v3 axis) instead of a single decision. Tracked as an ADR-0022 amendment + analyst-spec change;
> this report is the first case that motivates it. No single-bucket decision is recorded for this run.

---

## KNOWLEDGE-UPDATE

```
KNOWLEDGE-UPDATE: entity [[minervini-vcp-breakout]] — PRD clean-data re-run (2026-06-13, config_hash
81a1d38ee0a6, udgaard 1.0.93) reproduces the firewall record: OOS CAGR 12.9% / continuous 14.0%, edge
3.66-4.29/trade, Sharpe 0.49-0.66, Calmar 0.32-0.34, maxDD 44% (continuous, real path), 9/22 negative
windows, SPY-baseline FAIL. Confirms the all-weather REJECTED record on clean data. Regime decomposition:
THRUST +7.45 (Grade B, N=169), CRISIS −3.05 (Grade A, N=68, loses in confirmed crisis),
GRIND/NARROW/CHOP uncitable (Grade D). Entity "Assessment 2026-06-13" section already aligns.
```
```
KNOWLEDGE-UPDATE: concept [[strategy-assessment]] (or a new failure-mode/methodology note) — the
per-sector league table (§7) is the durable AUDIT TOOL that localizes bad-print contamination a blended
headline hides. The invalidated 2026-06-12 dev run (VPI $65k / LAF $26.4k split-adjustment failures,
~166 symbols flagged) inflated edge ~24× (3.7→90.9) and CAGR ~25pts (12.9→37.7%), made the MC
degenerate, and SPURIOUSLY PASSED the SPY baseline. The clean PRD re-run reproduced the firewall record.
Lesson: when an assessment headline diverges sharply from the firewall record, read the per-sector /
per-stock breakdown FIRST — single-digit per-sector edges with no monster row = clean; a multi-thousand-%
sector cell = un-caught bad print. Run assessments on PRD (clean universe), not dev.
```
```
KNOWLEDGE-UPDATE: concept [[regime-read-out]] / [[participate-and-lose]] — the candidate's deployment
profile is the mechanical signature of the death: avg ~7 of 10 slots filled, flattening to near-cash
ONLY in outright index downtrends (2001: 1 trade, 2008: 2). It does not sit out narrow-leadership chop —
the real 44% maxDD is a 2021→2024 still-unrecovered slide (NOT a 2008 event), confirming the loss lives
in the cross-section through chop, exactly where spyTrendUp keeps the book deployed.
```
```
KNOWLEDGE-UPDATE: methodology note — Block-B proxy-vs-firewall divergence is a clean worked example of
"proxy slice ≠ block verdict": the 25y single-cadence spine bucketed to the Block-B date range shows 3
negative windows (2014/15/16) where the firewall's dedicated Block-B run shows 0 + 20.8% in-mkt CAGR.
Different IS-anchoring + window phasing. Never present a spine block-slice as overturning the firewall.
```
