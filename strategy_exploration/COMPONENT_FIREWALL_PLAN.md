# Component Firewall — validation plan (Track 1)

_Created 2026-06-03. **This is a scoping/review doc, not executable and not yet a skill.** Nothing fires until the run plan (§3) is approved; no `/validate-component` skill is persisted until the gate thresholds (§4) + days-in-market definition (§6) get quant sign-off (memory `feedback_get_expert_review_before_persisting`). Adapts the quant-drafted Component Firewall table in `MINERVINI_VCP_STRATEGY_DEVELOPMENT.md` (2026-06-02)._

## 0. Where the candidate is

The Minervini VCP **breakout** strategy is a **regime-conditional COMPONENT** (flat in bear/chop; portfolio target 25% CAGR, NOT standalone 30%). Promotion + G14 are done:

- VCP-A → `narrowingRange(stepWindow=10)`, VCP-B → `volumeDryUp(10/50/0.7)` — first-class, unit-tested, deployed (udgaard 1.0.81).
- **G14 `/verify-promotion` PASS** (946=946 trades, Jaccard 1.0, 0 divergences over 25y). C14 satisfied — the candidate is no longer void-on-inline-scripts.

**Two candidates share this entry stack**, differing only in exit (G14 covers both, since it isolates the entry-condition implementation):
- **EX-ATR20×SSM** — `stopLoss(2.0 ATR)` OR `priceBelowEma(50)` (best DD, budget-consistent)
- **EX-VCPOLD×SSM** — `emaCross(10,20)` OR `stopLoss(2.5 ATR)` OR `stagnation(3%,15d)` (best edge/win%/sample)

Ranker LOCKED = `SectorStrengthMomentum`. Sizer `AtrRisk(1.25%, 2.0 ATR)`, maxPos 10, capital 100k, leverage 1.0, full universe `STOCK`, seed 42, entryDelayDays 1, 36/12/12 cadence.

## 1. Why not the v4 `/validate-candidate run-pipeline.sh` as-is

The v4 firewall **false-rejects a cash-in-crisis regime component by design** (quant 2026-06-02):

| v4 gate | Why it mis-fires on a regime component |
|---|---|
| **G1** CAGR ≥ 30% | Blended whole-period CAGR is dragged down by the windows where the component correctly sits in cash. The 30% floor belongs on the *in-market* period, not the blend. |
| **G6/G7** regime-positive mandates | Mandate the component be *positive* in 2008/2020/chop. A component whose thesis is "stand aside in those regimes" has ~no trades there — G6/G7 are unreachable, not failed. |
| **G8** ≥30 trades/window | A stand-aside window has near-zero trades by design; G8 reads that as a liquidity failure. |

The Component Firewall **validates alpha in-market and discipline in-cash separately — never credits cash, never penalizes it.**

## 2. What stays IDENTICAL to v4 (do not re-litigate)

- **Block structure + ranges:** A 2000-01-01→2014-01-01 · B 2014-01-01→2021-06-30 (COVID-inclusive) · 25y 2000-01-01→2025-12-31 · C 2021-01-01→2025-12-31 (informational). Quant-verified boundaries.
- **Cadence:** walk-forward 36/12/12 (IS 36mo / OOS 12mo / step 12mo), IS-derived sector ranking for OOS (WalkForwardService already does this — no leakage).
- **G10 design isolation** (freeze config across blocks), **G11 cross-block edge decay** (edge_B ≥ 0.5·edge_A), **G14** (done).
- **3 binding layers + informational Block C** philosophy (Block A + Block B + 25y aggregate bind; C is a yellow-flag sanity check).

## 3. Raw runs to fire (the data the gates need)

Two data sources are required because **walk-forward exposes no per-trade list** (same limitation that forces G14 to use single backtests):

| Run kind | Endpoint | Purpose | Per candidate |
|---|---|---|---|
| **Walk-forward per layer** | `POST /api/backtest/walk-forward` | per-window OOS edge/CAGR/DD/Sharpe/trade-count + `outOfSampleStatsByEntryMonth` (regime sub-gates) + `aggregateOosRiskMetrics` | 4 runs: A, B, 25y, C |
| **Single backtest per block** | `POST /api/backtest` → `GET /{id}/trades` | per-trade entry/exit dates → **days-in-market** (C1a) + window regime classification | up to 4 runs (or reuse the 25y G14 export for the 25y layer) |

- **Run ONE candidate end-to-end first** (propose **EX-ATR20×SSM**, the cleaner all-first-class exit) to *calibrate* the gate thresholds + the days-in-market formula, get quant sign-off, **then** run EX-VCPOLD×SSM through the frozen gates. Don't calibrate on both simultaneously (that's two-config snooping).
- **Sequential only** (engine OOMs on concurrent backtests). Stop dev containers first. Rough cost ≈ v4 pipeline (~60–90 min WF per candidate) + ~4 single backtests (~minutes each).
- The walk-forward template is a `WalkForwardRequest` with the candidate's `entryStrategy`/`exitStrategy`/`ranker`/`positionSizing` + `inSampleMonths 36, outOfSampleMonths 12, stepMonths 12`; the pipeline overrides only `startDate`/`endDate` per block.

## 4. Component Firewall gates — DRAFT (★ = data-gated, PENDING QUANT SIGN-OFF)

Population column states which trades each gate reads. **In-market** = participating windows only; **cash** = stand-aside windows (see §5); **blended** = whole period including cash.

| Gate | Threshold | Population | v4 origin |
|---|---|---|---|
| **C1a ★** in-market (active-period) CAGR | **≥ 30%** | in-market | replaces G1 (the real alpha bar) |
| **C1b** blended CAGR (anti-lottery) | **≥ 12%** | blended | new |
| **C1c** Sharpe ≥ 0.8 AND Calmar ≥ 0.5 | as stated | blended | = G9 |
| **C2** aggregate max DD | ≤ 25% | blended | = G2 |
| **C3** worst participating-window DD | ≤ 20% | in-market | = G3 |
| **C5** CoV of per-window edges | ≤ 1.5 | **in-market only** | = G5 (cash windows excluded — a flat window isn't "consistent edge") |
| **C6-STAND-ASIDE ★** crisis/cash windows | DD ≤ **3%** AND in-market days ≤ **15%** | cash | replaces G6 (cash side) — proves discipline, excluded from edge gates |
| **C6-IN-MARKET** participating windows | edge > 0 (v4-strict) | in-market | = G6 (in-market side) |
| **C7** ≤ 1 negative participating window, passing the W4 acceptance rule | as stated | in-market | replaces G7 |
| **C8 ★** in-market windows ≥ 30 trades; cash windows exempt; **N_min = 5** | as stated | per window | replaces G8 |
| **C9** Sharpe ≥ 0.8 ∧ Calmar ≥ 0.5 | (folded into C1c) | blended | = G9 |
| **C11** edge_B ≥ 0.5 · edge_A | as stated | in-market | = G11 |
| **C12** ≥ 100 trades per block | ≥ 100 | blended | = G12 |
| **C14** scripts promoted + G14 PASS | ✅ done | — | = G14 |
| **Portfolio-blend G6** (book survives 2008 + 2020) | DEFERRED | — | new — needs ≥ 2 components |

**Interim KEEP bar (standalone-as-component):** C1a ∧ C6-STAND-ASIDE ∧ C2 ∧ C3 ∧ C5 ∧ C9 ∧ C12 ∧ C14 ∧ (≤ 1 negative participating window passing the W4 rule). The true portfolio-contribution test is deferred until a 2nd component exists.

**W4/2011 acceptance rule (already agreed):** W4 is the one negative *participating* window (component held trades with `spyTrendUp` true, got chopped in 2011's narrow-breadth tape; win rate 17–24% vs ~40%; W4 maxDD 15.5–17.4% < 20%). It is a **disclosed portfolio coverage gap**, bounded-accepted for Track 1 — **not** rescued with a post-hoc regime filter (that's IS-fitting/ARS). Track 2 addresses it structurally as a *new* candidate.

## 5. Regime classification (window-level) — DRAFT

Each OOS window is labelled exactly once:

- **STAND-ASIDE (cash)** if the window's in-market days ≤ 15% **OR** trade count < N_min (5). These are evaluated ONLY against C6-STAND-ASIDE (discipline) and are **excluded** from C1a / C3 / C5 / C6-IN-MARKET / C7 (never credited, never penalized).
- **PARTICIPATING (in-market)** otherwise. These carry the alpha gates.

Emit a **regime-attribution table** (window → label → edge/CAGR/DD/trades/in-market-days) so the classification is auditable, not a black box. Open question: should the ≤15%-days and <5-trades conditions be AND or OR? (Drafted as OR — either signal alone means the component wasn't really deployed.)

## 6. Days-in-market computation (C1a) — the open definitional question

**It is now computable** (the per-trade export carries `startDate` + `quotes[]` date spans + `tradingDays`). But the *right denominator* is unsettled, and it changes C1a materially:

**Grounding fact (25y, EX-ATR20×SSM, from the G14 export):** the union of all days with ≥1 position open ≈ **5,410 of ~6,540 trading days (~83%)**. That **portfolio-union fraction is the WRONG denominator for C1a** — it says "something was almost always open" because the book holds up to 10 concurrent names across 25y of mostly-bull tape. It does not isolate "participating vs standing aside."

Two candidate definitions for "in-market CAGR" (quant to choose):
- **(a) Participating-window annualization** — drop the STAND-ASIDE windows (§5) entirely; annualize the realized return over only the participating windows' calendar span. Matches the C6 stand-aside split; recommended.
- **(b) Capital-deployment-weighted** — annualize by *deployed-capital-time* (Σ position notional·days / capital·days). Finer but conflates sizing with regime.

C1a's **30%** floor must be re-read against whichever denominator is chosen — the number was set conceptually, not calibrated. **Settle (a) vs (b) before C1a binds.**

## 7. The data-gated thresholds needing calibration + quant sign-off

Per `feedback_get_expert_review_before_persisting`, these stay as text until signed off:

1. **C1a 30%** — on which in-market denominator (§6a vs §6b)? Calibrate on the EX-ATR20×SSM block runs.
2. **C6-STAND-ASIDE** — DD ≤ 3% and in-market days ≤ 15%: are these the right discipline bounds for *this* component's actual cash windows (2001/2002/2008)?
3. **C8 N_min = 5** — confirmed in principle (2026-06-02); re-confirm against the real per-window trade counts.
4. **C5 in-market-only** and **C7 W4 rule** — confirm the in-market-only scoping and the single-negative-window allowance.
5. **§5 classifier** — AND vs OR on the two stand-aside conditions.

## 8. Orientation numbers (25y SINGLE backtest — NOT the firewall inputs)

⚠️ These are a **single backtest over 25y** (no IS/OOS split, optimistic vs walk-forward) — orientation only. The real gate inputs come from the §3 walk-forward runs.

| Metric (EX-ATR20×SSM, 25y) | Value | Note vs draft gate |
|---|---|---|
| Total trades | 946 | C12 (≥100/block) almost certainly fine |
| Blended CAGR | 10.4% | **under C1b 12%** — but blended; C1a (in-market) is the real test |
| Edge / trade | 3.63% | healthy |
| Win rate | 33.9% | sub-50%, payoff-carried (on-thesis) |
| Profit factor | 2.47 | strong |
| Sharpe | 0.77 | **just under C1c 0.8** (blended; depressed by cash drag) |
| Sortino | 1.08 | — |
| Calmar | 0.21 | **well under C1c 0.5** (blended) — flag for quant: is C1c the right population? |
| SPY correlation / beta | 0.36 / 0.43 | low — good diversifier (portfolio thesis) |

**The Calmar 0.21 / Sharpe 0.77 / CAGR 10.4% blended numbers are exactly the tension the Component Firewall exists to resolve** — they're depressed by the cash/crisis drag the v4 firewall would penalize. Whether C1c should read *blended* or *in-market* is the single biggest calibration question. Do NOT pre-judge from these single-backtest numbers — the walk-forward OOS per-block decomposition is what decides it.

## 9. Open decisions before firing / persisting

1. **Approve the §3 run plan** (EX-ATR20×SSM first, full WF + single-backtest set).
2. **Quant sign-off on §4 thresholds + §6 denominator + §5 classifier** (the ★ items) — ideally drafted text → quant review → then encode.
3. Only after 1+2: build `/validate-component` as a skill (reusing v4's block-firing + WF plumbing, swapping the evaluator for the Component gates).
4. Track 2 (breadth-gated *new* candidate) stays queued — not part of this validation.

## Reference
- `MINERVINI_VCP_STRATEGY_DEVELOPMENT.md` (authoritative candidate record; Component Firewall table 2026-06-02)
- `.claude/skills/validate-candidate/` (v4 firewall — block ranges, cadence, G10/G11/G14, eval-block.py to adapt)
- Memories: `feedback_get_expert_review_before_persisting`, `feedback_min_cagr_tradable`, `project_regime_conditional_portfolio_framework`, `feedback_mean_reversion_pullback_known_weakness` (W4 discipline)
