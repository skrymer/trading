# MR3 Strategy Development

_Status: REJECTED at Block A (2026-05-28). Multi-dimensional drift (G3 + G4 + G5 all tight failures). Three-axis re-design needed before next firewall entry._

## Hypothesis

Mean-reversion in an established uptrend: take long entries after a 3-day cumulative decline in stocks where EMA20 > EMA50 (uptrend intact) and the entry-day candle reverses (close > open). The thesis: shallow pullbacks in trending stocks mean-revert quickly, capturing 3-5 day swings.

Counterpoint to VZ3: VZ3 is trend-following with mean-reversion-style timing; MR3 is mean-reversion proper. Their best/worst regimes are inverse (MR3 best in high volatility, VZ3 best in steady trends).

## Current configuration (frozen 2026-05-28)

### Entry strategy

2-of-2 conditions must pass:

1. **`marketUptrend`** — predefined market-regime gate (breadth-EMA based)
2. **3-day decline in EMA20>EMA50 uptrend with reversal candle** (inline `script`):
   - `close[-3] > close[-2] > close[-1]` (cumulative 3-day decline)
   - `closeEMA20 > closeEMA50` (uptrend intact)
   - `close > open` (entry-day candle is green = reversal signal)

### Exit strategy

Any of:
- **Held ≥ 3 trading days OR +6% gain** (inline `script`)
- **Stop-loss**: 2.5 × ATR from entry (`stopLoss` condition)

### Ranker

`Volatility` — ranks candidates by ATR/close (highest first). Higher volatility = higher rank — fits the mean-reversion thesis (more volatile stocks revert farther).

### Position sizing (baseline)

```
AtrRisk(riskPercentage=1.25%, nAtr=2.0)
maxPositions=15, leverageRatio=1.0, entryDelayDays=1
startingCapital=10000
```

### Source request

`/tmp/screen-req-MR3-s1.json` — random seed 1 (best of 3-seed screen).

## /strategy-screen results (2005-2015, 7 OOS windows)

3-seed mean: Edge 0.27% / Sharpe 2.10 / **CAGR 35.21%** / MaxDD 18.20% / Calmar 1.94. Best seed: s1 — edge 0.28% / Sharpe 2.29 / **CAGR 36.77%** / MaxDD 17.70% / Calmar 2.08. All 5 screen gates passed.

## Firewall results (2026-05-28)

### Block A (2000-2014, 11 OOS windows) — **FAIL**

| Gate | Value | Threshold | Status | Tight? |
|---|---|---|---|---|
| G1 CAGR | 43.83% | ≥ 30% | PASS | — |
| G2 agg DD | 20.47% | ≤ 25% | PASS | — |
| **G3 worst-window DD** | **20.47%** | **≤ 20%** | **FAIL** | YES (2.4% relative — within 5% pct band) |
| **G4 positive pct** | **8/11 = 72.7%** | **≥ 75%** | **FAIL** | YES (off-by-1: 9 windows needed, got 8) |
| **G5 CoV edge** | **1.77** | **≤ 1.5** | **FAIL** | YES (18.0% relative — within 20% ratio band) |
| G6 2008 GFC | edge = 1.186 | > 0 | PASS | — |
| G7 chop | 2004 = 0.634 | ≥ 1 of {2004, 2011, 2015-H1} | PASS | — |
| G8 min trades | 364 | ≥ 30 per window | PASS | — |
| G9 Sharpe/Calmar | 2.58 / 2.14 | ≥ 0.8 / ≥ 0.5 | PASS | — |
| G12 block trades | 4830 | ≥ 100 | PASS | — |

**Three tight failures = multi-dimensional drift** (NEAR_MISS caps at 2 tight failures; 3+ falls into REJECTED-with-drift bucket). Per the quant: the pattern says "systematically slightly-off-mandate across multiple dimensions" — structural, not iterational.

### Block B — NOT RUN (gated by Block A pass)

### Per-window Block A detail

| Window | OOS range | CAGR | Edge | Trades | DD |
|---|---|---:|---:|---:|---:|
| W1 | 2003-2004 | 116.20% | +1.21% | 529 | 5.69% |
| W2 | 2004-2005 | 51.46% | +0.63% | 443 | 7.67% |
| W3 | 2005-2006 | 63.66% | +0.30% | 446 | 13.44% |
| W4 | 2006-2007 | 24.93% | **−0.28%** | 458 | **20.47%** |
| W5 | 2007-2008 | 42.30% | +0.31% | 419 | 7.81% |
| W6 | 2008-2009 | 74.20% | +1.19% | 422 | 10.07% |
| W7 | 2009-2010 | 21.89% | +0.58% | 485 | 16.15% |
| W8 | 2010-2011 | 18.07% | **−0.37%** | 456 | 16.95% |
| W9 | 2011-2012 | **−5.52%** | **−0.74%** | 364 | 17.70% |
| W10 | 2012-2013 | 40.75% | +0.13% | 426 | 8.78% |
| W11 | 2013-2014 | 89.35% | +1.08% | 382 | 5.98% |

Three negative-edge windows (W4 2006, W8 2010, W9 2011) drive G4 + G5 failures. W4 also caps G3 with 20.47% DD. The pattern: edge bleeds in low-volatility / range-bound markets (2006 chop, 2010-2011 EU-debt aftermath chop), exactly the regimes where mean-reversion dips aren't deep enough to revert.

## Verdict

**REJECTED** with multi-dimensional drift. Per the firewall: re-design + re-survey via `/strategy-screen` before re-entering.

## Improvement recommendations (quant, 2026-05-28)

Mean-reversion premise needs sharper qualification. Three correlated structural failures suggest the strategy is firing on dips that don't qualify as real pullbacks, in regimes where mean-reversion edge is weakest. Three levers, all required:

| Lever | Change | Why |
|---|---|---|
| **Stock-level ATR-percentile floor** | Require trailing 252-day `ATR/close` ≥ 30th percentile of stock's own history | Suppresses entries in the stock's own quiet regime — where mean-reversion edge is weakest |
| **Minimum pullback depth** | Require cumulative 3-day return ≤ −3% | Rejects shallow dips that don't qualify as a real pullback |
| **Exit** | Held ≥ 5 days + 2×ATR stop (was held ≥ 3 days + 2.5×ATR) | Gives the reversion more time to materialize; tighter stop reduces small losers |

**Priority**: lower than VZ3 (multi-axis vs single-axis). Treat as a separate candidate (call it `MR4`); re-survey via `/strategy-screen` to verify the levers actually fix the 3 tight failures before re-entering the firewall.

## Open questions / structural notes

- **Inline `script` conditions block tradability**: entry pullback + exit hold-or-target are both inline scripts. Even after a successful re-design + firewall pass, MR4 would be **TRADABLE-PENDING-PROMOTION** until the scripts are promoted via `/create-condition`.
- **W1 2003 outlier**: 116.20% CAGR / +1.21% edge / only 5.69% DD. The post-dot-com bottom + mean-reversion thesis = ideal regime. Worth confirming the trades aren't fixture-driven on a few mega-winners.
- **Pair potential with VZ3**: largely uncorrelated by regime preference (MR3 best in volatility = 2003/2008; VZ3 best in trending bulls = 2009). If MR4 (post-re-design) clears the firewall and VZ3-upsized clears, the pair is interesting — but each candidate must independently clear before pair-testing.

## Roster status

- **Position in roster**: second priority after VZ3 (multi-axis re-design vs single-axis fix). No active work on the engine side; queued behind VZ3 sweep + 2019 drag fix.
- **Source data**: `/tmp/screen-req-MR3-s1.json`, `/tmp/validate-MR3-s1-blockA.json`, `/tmp/validate-MR3-s1-summary.json`, `strategy_exploration/validate-MR3-s1.md`
