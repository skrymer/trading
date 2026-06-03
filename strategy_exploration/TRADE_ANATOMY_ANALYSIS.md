# Trade-anatomy analysis — Minervini VCP breakout (Track-1, 25y)

_Created 2026-06-03. Source: the 946-trade export from the Track-1 G14 single backtest (25y 2000–2025, EX-ATR20, `spyTrendUp`, full ~3,900 universe), `/tmp/verify-promotion-minervini-vcp/inline-trades.json`. Reproduce with `diagnostics/trade_anatomy.py [trades.json]` + `diagnostics/entry_discrimination.py [trades.json]` (join market breadth from `GET /api/breadth/market-daily`). This is a **diagnostic to understand the premise**, not a rescue of the REJECTED config — every "tighten X" reading below is a hypothesis for a NEW candidate, not a tweak to this one (acting on within-sample slices = IS-fitting/ARS)._

## 1. The edge is a handful of monster winners — the rest is noise

| metric | value |
|---|---|
| win rate | 33.9% |
| avg win / avg loss | +20.5% / −5.0% (payoff 4.07×) |
| hold: winner vs loser | 88 vs 24 days (cut-losses/let-run working) |
| median trade | −2.4% |

**Concentration (the headline finding):** top **5** trades = **35%** of all P/L · top 10 = 53% · top 20 = 75% · **top 50 = 115%.** That last number means **trades #51–946 collectively LOSE ~500 percentage points** — the entire 25y return is carried by ~50 monster winners; the other ~900 trades net negative. The premise is structurally a **rare-event lottery on catching emerging super-performers**, not a "win more often" edge.

## 2. Market breadth at entry does NOT predict winners (the root of the Track-2 failure)

| entry breadth | n | win% | mean P/L |
|---|---|---|---|
| <30 (narrow) | 147 | 34% | +1.8% |
| 30–45 | 265 | 33% | +2.4% |
| 45–60 | 315 | 35% | +4.1% |
| >60 (broad) | 219 | 33% | +5.7% |

**Win rate is flat (~33–35%) at every breadth level.** Monsters entered across all levels — NIO +139% at breadth 24, ARWR +94% at 34, EVLV +88% at 39 (very narrow tape). Higher breadth lifts *average* payoff slightly but not *frequency*. So a breadth filter removes winners and losers in equal proportion → it can only **thin** (and risks cutting the rare monsters). This is the empirical root of why Track-2 (scalar breadth gate) thinned-and-deepened, and predicts a sector-breadth gate (Track-2b) likely behaves the same.

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

## 6. Strategic conclusions

1. **A market *condition* (entry breadth gate) is dead** — breadth at entry doesn't predict winners (§2). Confirmed by Track-1/Track-2 thinning.
2. **An entry "regime condition" fit to the monster years (2003/09/13/20) would be IS-fitting** — encoding the peeked-at answer. Not a clean fix. The **regime dimension belongs at the PORTFOLIO/transition layer** (this is a recovery-trend *specialist*; pair with a chop/crisis component — the regime-conditional framework).
3. **Regime timing doesn't cure the concentration.** Top-50 = 115% means missing the 5 biggest monsters *inside* a good regime still loses. Regime changes *when* you deploy; the binding lever is **monster-selection** — entry quality (ADX, tighter breakout) + the ranker surfacing the monster — not a regime gate bolted on entry.
4. **This is why both gate experiments thinned.** Neither spyTrendUp nor breadth separates monsters from noise; the discriminators that *do* (ADX, proximity-to-high, momentum/RS-extremes) live in the entry/ranker, not the market gate.

## 7. QUEUED diagnostic (post-Track-2b) — condition ablation, ADX-add first

Rigorous version of §4 — needs backtests (one run at a time, queues behind Track-2b):
- **ADX-add (headline):** baseline 10-condition entry + `adxRange(minADX=30, maxADX=100)`, 25y single backtest. Measure vs baseline: trades, win%, payoff, **right-tail capture (top-10/top-50 monsters retained)**, in-market CAGR. The decisive question — does ADX **select** (lifts win%/payoff, keeps monsters) or just **thin** (cuts trades, drops monsters)? Only "select" is additive.
- **Full leave-one-out ablation:** drop each of the 10 entry conditions in turn (11 single backtests + baseline), measure the delta on the same metrics → which conditions ADD edge vs which only THIN. Especially: is the VCP base (narrowingRange/volumeDryUp) load-bearing or dilutive? Is RS≥70 the load-bearing gate?
- **Discipline:** this is DIAGNOSTIC to inform a NEW candidate's design / the deprecation call — NOT tuning this REJECTED config to pass. Findings go to the quant before any become a candidate (anti-IS-fitting). None of it changes the concentration reality (§6.3).

## Reference
- `COMPONENT_FIREWALL_PLAN.md` §10 (Track-1 REJECTED), `TRACK2_BREADTH_GATE_PLAN.md` §8 (Track-2 REJECTED + Track-2b)
- [[project-minervini-vcp-breakout-rejected]], `feedback_lottery_screen_diagnostic`, `feedback_mean_reversion_pullback_known_weakness`, `project_regime_conditional_portfolio_framework`
- Scripts: `diagnostics/trade_anatomy.py`, `diagnostics/entry_discrimination.py`
