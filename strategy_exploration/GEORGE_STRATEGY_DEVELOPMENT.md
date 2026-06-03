# George — 52-Week-High Anchoring strategy development

_Created: 2026-06-04 · Status: **DEPRECATED — capped premise (lost to Random baseline) 2026-06-04**._
_First candidate of the research-widened search (`STRATEGY_LEDGER.md` §A). Quant-signed throughout._

> **⛔ VERDICT (2026-06-04): CAPPED PREMISE — DEPRECATED (long-only liquid form).** The
> nearness-to-52wk-high ranker **did not beat a byte-identical Random baseline**: Random matched
> per-trade edge (~1%) and beat George on blended CAGR (6.6 vs 1.1%), positive windows (6/7 vs
> 5/7), DD, and GFC survival (−2.1 vs −14.3%). The ~1% edge is **entry-universe beta**, and the
> anchoring tilt is a *worse-than-noise* GFC liability (concentrates into the most-extended
> momentum names). George's win-rate (53.4 vs 50) + WFE (1.21) wins are payoff-shape artifacts, not
> alpha. **No defended successor** (it would defend a no-information selector). The anchoring
> *class* was tested in its weakest habitat (long-only — the engine can't express George-Hwang's
> short leg; liquidity-pre-filtered — stripped the down-cap tier where the effect survives); the
> only honest re-test is a long-short decile on a down-cap universe, **not buildable in a long-only
> engine** → class deprecated in the tradable universe. **Reusable artifacts kept:** the
> `nearness52WeekHigh` ranker + `maxHoldingDays` / `belowPercentOf52WeekHigh` conditions (PR #90)
> are general, tested building blocks. Full method: `BACKTESTING_FUNNEL.md` §6.

## Premise

George & Hwang (2004, *J. Finance*): a stock's **nearness to its own 52-week high** is a
standalone cross-sectional return predictor — winners *approaching* their high keep winning.
It is a price-**LEVEL** signal (anchoring on the record price), not a return-**CHANGE** signal,
and predicts future returns "whether or not stocks had extreme past returns." In the original
it beat Jegadeesh-Titman 12-1 momentum and industry momentum head-to-head (0.65 vs 0.38 vs
0.25 %/mo, Jul1963–Dec2001 CRSP). _(That 0.65 is the long-SHORT decile spread; we trade only
the long top-tranche leg — it captures the winners-near-high underreaction, not the full spread.)_

**Why it is distinct (not a re-run of a deprecated class):**
- **Not RS-momentum rotation** (deprecated §B-4): a price-LEVEL ratio, not a trailing-return rank.
- **Not the Minervini breakout** (deprecated §B-2): as a *ranker* it selects names *approaching*
  the anchor across the whole cross-section; it never requires a fresh-high *event*, so the
  breakout's follow-through-failure-at-a-fresh-high death does not mechanically transfer. (The
  proximity-*condition* form — `Mimir` in the ledger — does still carry that risk and is screened
  separately as a falsification test.)

## Status / funnel

| Stage | State |
|---|---|
| Spec | ✅ quant-signed |
| Build (ranker + 2 exits, TDD, first-class) | ✅ done (PR #90) |
| `/strategy-screen` 2005–2015 | ✅ run — **FAIL** G2 (Sharpe 0.14) + G4 (GFC DD 44.7%) |
| **G-RANDOM baseline** | ✅ run — **George LOST to Random** (capped premise) |
| `/validate-candidate` … `/monte-carlo` | ⛔ not reached — DEPRECATED at screen |

**Screen numbers (2005–2015, 7 OOS windows, seed 42):**

| Metric | George (nearness52WeekHigh) | Random (identical skeleton) |
|---|---|---|
| Edge / trade | +1.01% | **+1.08%** |
| Blended OOS CAGR | +1.08% | **+6.63%** |
| Aggregate maxDD | 51.3% | **46.1%** |
| Win rate | **53.4%** | 50.0% |
| Positive windows | 5/7 | **6/7** |
| WFE | **1.21** | 0.86 |
| 2008 GFC window edge | **−14.3%** | **−2.1%** |
| Trades | 556 | 1695 |

Per-window George OOS edges: [−14.3, +3.1, +8.0, −0.2, +7.9, +12.3, +2.7]. The 2008 window alone
sinks the aggregate; the Random baseline shows even that aside, the ranker carries no information.

## The new artifact to build (TDD)

A **strategy-neutral** ranker (the ranker names the *mechanic*; "George" names only the assembled
strategy, per convention):

`NearnessTo52WeekHighRanker : StockRanker`
- `score(stock, entryQuote) = min(entryQuote.closePrice / entryQuote.high52Week, 1.0)`
- returns `-Double.MAX_VALUE` when `high52Week` is null (< 252 bars) / `≤ 0`, or `closePrice ≤ 0`
  (an unscoreable name must rank **last**, not score 0 — 0 would beat a legitimately-decayed name
  at ratio 0.4 and corrupt the ordering; mirrors `TrailingReturnRanker`'s insufficient-history sentinel).
- register in `RankerFactory` as type `nearness52WeekHigh` + catalog metadata.

**The load-bearing design call — cap the score at 1.0** (strategy-neutral rationale for the KDoc):
a name *at or above* its 52-week high is the *most* anchored-to-high name there is, so it belongs at
the top of the ranking. But an **uncapped** ratio (e.g. a name gapped 8% above its high → 1.08) would
rank an *overshoot/breakout* above a name sitting *at* its high — which is a momentum-overshoot signal,
not an anchoring signal. Capping at 1.0 collapses "at-or-above the high" into one maximally-anchored
tranche (tie-broken by jitter), keeping this a **nearness** ranker rather than a breakout ranker.
Excluding fresh-highs would be equally wrong — it would carve the most-anchored cohort out of the top.
No low-side winsorization (a ratio of 0.3 is a legitimately-far name that *should* rank low).

## Candidate spec (assembled strategy "George")

Everything except the ranker uses existing first-class conditions.

| Element | Spec | Rationale |
|---|---|---|
| **Entry stack (AND)** | `minimumHistoryDays(365)` · `averageDollarVolumeAbove($10M, 20d)` · `minimumPrice($5)` | **Tradability-only** filters, no signal view — lets the ranker do all selection (faithful to George-Hwang ranking the whole cross-section). $10M is *looser* than the $50M default to keep the small/mid-caps where anchoring is strongest (McLean-Pontiff: edges live down-cap). **No** trend/breadth gate, and **no** `percentFrom52WeekHigh` entry gate (that would double-count the dimension the ranker already owns and convert a soft preference into a hard cutoff). |
| **Ranker** | `nearness52WeekHigh`, pinned `randomSeed` | the new build; fresh-high names all score 1.0 → real ties → pinned seed for reproducibility |
| **Exit (script, screen-only)** | `daysInTrade ≥ 126` **OR** `close / high52Week < 0.75` | time-cap (≈6-month hold = the paper's mechanic; also what makes the daily engine approximate decile *rotation*) + nearness-decay release (engine proxy for "fell out of the top decile"; 0.75 reuses the calibrated 25%-off-high threshold). **No stop / profit-target / EMA-cross** — those import a different premise's exit and confound the read. |
| **Sizer** | `PercentEquity` equal-weight ≈ `1/maxPositions` (~3.3%) | George-Hwang's decile is **equal-weight**. `AtrRisk` would underweight high-ATR names (near-high movers skew high-ATR) → confounds the anchoring read with a vol-sizing artifact. Equal-weight is the faithful, attributable-to-the-ranker-alone choice. |
| **maxPositions** | 30 | the paper's decile (~390 names) is capital-infeasible; 10 holds only the top-of-the-top (monster/seed luck). 30 equal-weight names reads the decile's *diversified* character and stays comparable to other ranker candidates. |
| **entryDelayDays** | 1 | rank on signal day, fill next — no same-bar look-ahead |
| **leverage** | 1.0 | long-only equal-weight cash; leverage is a later amplifier, not a screen-stage knob |
| **universe / window** | full STOCK (~3,900, incl. delisted) / 2005-01-01→2015-01-01 | survivorship-correct; standard `/strategy-screen` window |

## PASS / KILL on the first screen (relaxed `/strategy-screen` gates + 30%-CAGR flag)

**PASS (→ full Block-A `/validate-candidate`):** positive aggregate OOS edge clearing the relaxed
gates, **distributed across windows** (not 1–2), AND **clearly beats a `Random`-ranker baseline on
the identical entry/exit/sizer/maxPositions skeleton.** Flag (don't kill) if CAGR < 30%.

**KILL (reject at screen, do not iterate):**
- Edge in the noise band / fails relaxed gates.
- **Lottery pattern** — lumpy CAGR concentrated in 1–2 windows + several negative-CAGR windows
  (regime-detector, not anchoring alpha). Geometric compound of the lumpy sequence is the true number.
  Do **not** add a regime gate to rescue (IS-fitting the single OOS window).
- **Ties `Random`** — the ranker carries no information; the premise class is capped. Reject the
  *ranker premise*, don't tune the skeleton.
- **ARS** across the obvious neighbours ({0.70, 0.75, 0.80} decay floor; {120, 126, 132} hold) — if
  the result only holds at one knob, the parameter dimension is structurally wrong; reject the premise.

**Do NOT at screen stage:** sweep sizers, add stops/targets/trend/breadth gates, or widen the
universe to rescue a weak read — those are premise-changing rescues.

**Cheapest companion run:** the **`Random`-ranker baseline** on the same skeleton — the one-shot
capped-premise check.

## Discipline notes (carried to validation)

1. **George is NOT tradable even on a good screen** — the script exit's `126` / `0.75` are tunables
   that must be **promoted to first-class conditions** (`/create-condition`) and pass **G13 parameter
   robustness** + **G14 promotion invariance** before any TRADABLE claim. Script is the right *screen*
   path only.
2. **Naming:** "George" = the assembled strategy. The ranker and any promoted conditions stay
   **strategy-neutral** (describe the 52-week-high mechanic; no "George" in KDoc/metadata/test names).
3. **Down-cap robustness leg** (McLean-Pontiff): if George survives, re-run on a dollar-volume-filtered
   lower-liquidity sub-universe — an edge that *strengthens* down-cap is more believable. Post-screen,
   not a screen gate.

## Reference
- Source: George, T. & Hwang, C-Y. (2004), "The 52-Week High and Momentum Investing," *J. Finance* 59(5).
- Ledger: `STRATEGY_LEDGER.md` §C.2 (George row) + §C.3 (mis-partitioned-not-exhausted).
- Engine: `StockRanker.kt` / `RankerFactory.kt` (`TrailingReturnRanker` is the template + sentinel pattern);
  `StockQuote.high52Week`; `AverageDollarVolumeAboveCondition` / `MinimumHistoryDaysCondition` /
  `MinimumPriceCondition` / `ScriptExitCondition` (all exist — only the ranker is new).
