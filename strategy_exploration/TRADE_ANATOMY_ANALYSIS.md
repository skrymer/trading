# Trade-anatomy analysis — Minervini VCP breakout (Track-1, 25y)

_Created 2026-06-03. Source: the 946-trade export from the Track-1 G14 single backtest (25y 2000–2025, EX-ATR20, `spyTrendUp`, full ~3,900 universe), `/tmp/verify-promotion-minervini-vcp/inline-trades.json`. Reproduce with `diagnostics/trade_anatomy.py [trades.json]` + `diagnostics/entry_discrimination.py [trades.json]` (join market breadth from `GET /api/breadth/market-daily`). This is a **diagnostic to understand the premise**, not a rescue of the REJECTED config — every "tighten X" reading below is a hypothesis for a NEW candidate, not a tweak to this one (acting on within-sample slices = IS-fitting/ARS)._

## 1. Shape of the edge — concentrated right tail (the premise *signature*, not the killer)

| metric | value |
|---|---|
| win rate | 33.9% |
| avg win / avg loss | +20.5% / −5.0% (payoff 4.07×) |
| hold: winner vs loser | 88 vs 24 days (cut-losses/let-run working) |
| median trade | −2.4% |

**Concentration:** top **5** trades = **35%** of all P/L · top 10 = 53% · top 20 = 75% · **top 50 = 115%** (trades #51–946 net ~−500pp). ~50 monster winners carry the whole 25y return.

**⚠️ Read correctly (quant review 2026-06-03):** this concentration is **the signature of the breakout/momentum premise class, NOT a pathology.** A 4×-payoff / 34%-win system *mechanically* produces an extreme right tail — virtually every profitable trend system looks like this. The number is **true but not diagnostic**: it does NOT distinguish "viable momentum edge" from "lottery," and the firewall §10 verdict was explicitly **NOT lottery** (Block B is a genuine broad-regime edge: 0 negative windows, 20.8% in-mkt CAGR, real 2020 +56.5% recovery alpha). So do **not** call this a "rare-event lottery" — that contradicts §10 and over-reads a cosmetic statistic.

**The actual load-bearing diagnostic lives in `COMPONENT_FIREWALL_PLAN.md` §10, not here:** the 25y **in-market geometric CAGR 9.6% < blended 12.7% inversion** — the regime-aware tell that returns are *dispersion-dominated, not alpha-dominated*. The legitimate use of the concentration finding is narrow: it **bounds the upside of any monster-selection iteration** (if 50 monsters carry 115% and a better ranker can realistically retain maybe 60–70% of them, the within-strategy lever's payoff is capped and estimable). Signature + upside-ceiling — not a killer.

## 2. Market breadth at entry does NOT predict winners (the root of the Track-2 failure)

| entry breadth | n | win% | mean P/L |
|---|---|---|---|
| <30 (narrow) | 147 | 34% | +1.8% |
| 30–45 | 265 | 33% | +2.4% |
| 45–60 | 315 | 35% | +4.1% |
| >60 (broad) | 219 | 33% | +5.7% |

**Win rate is flat (~33–35%) at every breadth level** — but mean P/L **rises monotonically** (+1.8 → +5.7%). The honest claim (quant review): **breadth is a payoff-*magnitude* signal, NOT a win-*frequency* signal — and not strong enough to gate on.** (Not the overstated "breadth doesn't predict winners.") Because a gate thins *both* tails and breadth doesn't lift the hit rate, gating on it removes winners and losers in ~equal proportion → it can only **thin** (and risks cutting the rare monsters — NIO +139% entered at breadth 24, ARWR +94% at 34, EVLV +88% at 39). This is the empirical root of why Track-2 (scalar breadth gate) thinned-and-deepened, and predicts a sector-breadth gate (Track-2b) likely behaves the same.

## 3. Winners ARE clustered — by macro regime, not entry-day breadth

Big-return years: **2003 (+771%), 2020 (+448%), 2013 (+425%), 2006 (+355%), 2009 (+227%)** — all **post-washout strong-recovery uptrends** (post-dotcom / post-GFC / post-COVID), where fresh leaders emerge. Loser years: 2011, 2021–2023 (narrow chop), where monsters don't appear and noise dominates. The edge is regime-gated on "is this a fresh broad recovery that breeds new leaders" — a thing a daily breadth reading can't see at entry.

## 4. Which ENTRY characteristics discriminate (correlational, within-sample)

Gate conditions are all on the passing side by construction, so this shows *marginal* power within the passing range (true contribution needs a re-run ablation — §6):

| dimension (current gate) | finding | read |
|---|---|---|
| **ADX trend-strength** (NOT used) | win% **31→48%** as ADX <20→>40; mean +8% at 30–40 | **strongest unused discriminator → additive hypothesis** |
| dist to Donchian high (trigger ≤1.5%) | at/through high **43% win, +10.9%**; 1–3%-below = 31% | breakout works *at* the high; the "near" tolerance dilutes |
| % from 52wk high (≤25%) | <5% = 37% win; **15–25% slice = 23%** | loose ≤25% admits a dilutive far-from-high slice |
| % above 52wk low (≥30%) | 30–60% = +1.7%/3 monsters; **>100% = 34/50 monsters** | monsters are names already up 100%+; low slice dilutes |
| RS percentile (≥70) | win% **flat** 30–38%; 95–100 holds 20/50 monsters | admits a range; extreme-RS is monster-rich but lottery-like |

**Standout: ADX (trend strength) is a strong discriminator the strategy doesn't use.** The loose tails of three Minervini gates (%52wH, %52wL, Donchian "near") are dilutive — but tightening them on *this* path is IS-fitting; they're hypotheses for a fresh candidate.

## 5. Universe question — already at the ceiling; monster-catching is entry+ranker

- The config **already trades the full ~3,900 real-sector universe** (`assetTypes:["STOCK"]`, no symbol list). "Few trades" is the selective 10-clause entry × **maxPositions=10** concurrency cap, NOT universe size.
- The Minervini instinct (screen broad to net the few super-performers) is right *in principle*: since the edge is catching rare monsters, **more candidates = more right-tail shots**. The open levers: does the universe under-sample small/micro-caps and fresh listings (where monsters start)? Is the **liquidity filter** screening out tomorrow's monsters before they're liquid? And at maxPos 10, catching the monster is a **ranking** problem — the ranker must surface it from the field.

## 6. Strategic conclusions (quant-reviewed 2026-06-03)

**The binding reason the component is dead is §10's selector problem — participate-and-lose in narrow-up chop — NOT concentration.** Concentration is the premise signature (§1). Keep that ordering straight.

1. **A market *condition* (entry breadth gate) is dead** — breadth is a payoff-magnitude, not win-frequency, signal (§2), too weak to gate on; a gate only thins. Confirmed empirically by Track-1/Track-2.
2. **An entry "regime condition" fit to the monster years (2003/09/13/20) would be IS-fitting** — encoding the peeked-at answer. The **regime dimension belongs at the PORTFOLIO/transition layer**: this is a recovery-trend *specialist* (the risk-on leg), paired with a chop/crisis complement. Portfolio-layer regime does **exactly the job the entry-time selector failed at** — deciding whether to deploy the concentrated bet into monster-breeding tape — using out-of-sample transition signals, not an entry gate fit to this realization.
3. **The breakout edge is REAL but regime-bound** (Block B earned that). It is neither a fatal lottery nor a fixable-by-entry-gate problem — it is a **regime-discrimination** problem that a daily entry-time signal structurally can't solve. The within-strategy lever (entry quality + ranker) has a **bounded** upside (§1 ceiling) and **cannot fix the selector defect** — narrow-leadership chop contains plenty of high-ADX, near-high, high-RS individual names.
4. **Shelf it as a known-real risk-on building block** for when a *separately-validated* regime-transition layer exists to deploy it. Don't re-tune the entry stack on this realization to chase the upside ceiling — that's the same sunk-cost/IS-fitting trap as the regime gate, wearing an entry-quality hat.

### 6b. Sunk-cost guardrails (frozen)
- **Track-2b is the LAST entry-time-regime shot.** If it fails its frozen kill rule, the entry-time-regime search is DEPRECATED *as written* — **no Track-2c** (a third breadth flavor / ADX+breadth hybrid = searching the selector design space against one OOS realization = `feedback_aliased_regime_sensitivity`).
- **"Monster-selection is the within-strategy lever" is NOT a license to tune ADX/Donchian/RS on this config.** Any entry-quality finding (§4, §7) is a hypothesis for a *fresh* candidate, never applied to this REJECTED one.

## 7. QUEUED diagnostics (sequenced AFTER the Track-2b kill decision — quant 2026-06-03)

**Sequencing is load-bearing:** run these only *after* Track-2b's kill rule is adjudicated, so an ADX "select" result cannot read as a rescue narrative for a config that's about to be deprecated. All findings are **fresh-candidate hypotheses, never tweaks to this config**; they go to the quant before any becomes a candidate.

### 7a. Condition ablation (forward-design knowledge, NOT a rescue)
- **ADX-add:** baseline 10-condition entry + `adxRange(minADX=30, maxADX=100)`, 25y single backtest. Measure vs baseline: trades, win%, payoff, **right-tail capture (top-10/top-50 monsters retained)**, in-market CAGR. Decisive question — does ADX **select** (lifts win%/payoff, keeps monsters) or just **thin**? **Caveat (quant): even a clean "select" does NOT rehabilitate the premise** — ADX is an entry-quality filter, not a regime selector; narrow-leadership chop contains plenty of high-ADX names, so it can't fix the §10 failure mode. It's design knowledge for a *future* candidate that already has a working regime layer.
- **Full leave-one-out:** drop each of the 10 entry conditions in turn (11 single backtests + baseline), same metrics → which conditions ADD vs only THIN. Especially: is the VCP base (narrowingRange/volumeDryUp) load-bearing or dilutive? Is RS≥70 doing work? High-value, cheap design knowledge for the next risk-on candidate.

### 7b. Universe/liquidity POPULATION-BIAS audit (cheap, high-leverage — do this; drop "widen universe")
- **"Widen the universe to catch more monsters" is a DISTRACTION** — already at full ~3,900; the binding constraint is **maxPos 10 + the ranker**, not universe size. More candidates just give the ranker more to reject; more candidates ≠ more fills.
- **The real thread: survivorship / liquidity *truncation*.** Does the liquidity filter remove the *pre-liquidity* phase of the emerging small/micro-caps where monsters start? Does delisting handling keep names that entered and then blew up (left tail), or drop them? If the right tail is survivorship-inflated AND the left tail truncated, **the entire 946-trade sample — and thus §1/§2/§4 — is reading a biased population.** This is a **data-integrity audit, not a strategy lever** — it matters because *every future breakout candidate inherits the same population bias.*

  **AUDIT RESULT (2026-06-03) — sample is REPRESENTATIVE; survivorship-bias worry REFUTED:**
  - **Universe is survivorship-free by design.** Delisted symbols are included with full history through the delisting date; a position open at delisting is **force-closed as a real loss** (`EXIT_REASON_DELISTED`, `BacktestService.kt`, regression-tested). The 946 trades contain **21 delisting force-closes** — the left tail is captured, not silently dropped.
  - **No pre-liquidity truncation for this candidate.** The liquidity filter (`averageDollarVolumeAbove`, $50M default) is **optional and NOT in the Minervini stack**, so micro-caps enter freely: **35% of entries < $10, 19% < $5** (min $0.01), median $14.91. The monsters are genuine small-cap emerging names. (Future breakout candidates that *add* the liquidity filter WOULD left-truncate the growth arc — flag then, not now.)
  - **Loss tail clean:** only 2 trades worse than −20% (worst −31%); ATR stop + 50-EMA trail catch failures cleanly, no hidden gap-down population.
  - **One operational caveat:** delisted re-ingestion is *code-merged, full re-run pending* (~1,500 tickers; 21 delistings present = *some* ingested). If incomplete, the backtest is slightly **optimistic** (missing failure trades) → makes the REJECTED verdict **more** robust, never rescues it.
  - **Conclusion:** the concentration (§1) / breadth (§2) / discrimination (§4) findings are NOT survivorship artifacts. No rescue hides in the universe. Operational follow-up (independent of this strategy): complete the delisted re-ingestion so the failure population is fully represented for all future breakout work.

## Reference
- `COMPONENT_FIREWALL_PLAN.md` §10 (Track-1 REJECTED), `TRACK2_BREADTH_GATE_PLAN.md` §8 (Track-2 REJECTED + Track-2b)
- [[project-minervini-vcp-breakout-rejected]], `feedback_lottery_screen_diagnostic`, `feedback_mean_reversion_pullback_known_weakness`, `project_regime_conditional_portfolio_framework`
- Scripts: `diagnostics/trade_anatomy.py`, `diagnostics/entry_discrimination.py`
