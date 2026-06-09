# 18. Trailing-history rankers load pre-window warmup data, visible to scoring only

Date: 2026-06-09

## Status

Accepted

## Context

The walk-forward engine loaded each window's price quotes with a hard filter `QUOTE_DATE >= windowStart`
(`StockJooqRepository.findBySymbols(quotesAfter = after)`), with no lookback buffer. Each IS and OOS
window is loaded independently with `quotesAfter = window start`.

Most rankers and conditions read **precomputed per-quote indicators** (ATR, ADX, EMAs, Donchian,
52-week high, market-relative strength) computed during ingestion over full history — truncating the
loaded window does not strip a precomputed column, so they are unaffected.

But three rankers compute **trailing returns in-engine** from the loaded quote series:
`MarketResidualMomentumRanker` (504-day estimation window), the new `MultiFactorResidualMomentumRanker`
(504-day), and `TrailingReturnRanker` (252-day). For these, `estStart = entryIdx − estimationDays`; when
the loaded series starts at the window start, every early entry has `estStart < 0` and the ranker returns
its `UNSCOREABLE` sentinel.

Every OOS window across the whole funnel is **12 months** (`/strategy-screen` 36/12/12;
`/validate-candidate` Block A/B/25y 36/12/12; `/walk-forward` default 5y/1y). 12 months (~250 trading
days) is shorter than a 504-day lookback, so **a trailing ranker is unscoreable for every OOS entry.**
When all candidates score the sentinel, capital-aware "rank top-N" selection collapses to the tie-break
jitter RNG (`BacktestService.tieBreakRandom`) — a stream independent of the `RandomRanker`'s
per-`(symbol,date)` hash RNG. So a residual-ranker-vs-Random screen compared two different random draws
and measured nothing about selection skill. This invalidated the #130 `MarketResidualMomentum` screen
verdict (it had been read as "anti-selective beta-delivery"; that conclusion is withdrawn — see the
`mrm` wiki entity).

This is a data-loading artifact, not a real trading constraint: a live trader on entry day has the full
prior price history.

## Decision

A `StockRanker` declares the trailing history it needs via `warmupTradingDays(): Int` (default `0`;
overridden by the three trailing rankers to their lookback depth). Before a backtest, the engine loads
the stock, SPY, and sector-ETF series from a **warmup-buffered date** —
`after − ceil(warmupTradingDays × 365/252 × 1.10)` calendar days — covering the lookback with a 10%
margin for holidays/weekends.

The warmup bars are **visible to `StockRanker.score` only**. Entry-signal detection, the trading-date
range, trades, the stitched OOS equity/return curve (ADR 0005), the SPY-baseline Calmar leg (ADR 0013),
and the per-entry-month OOS buckets (ADR 0006) all stay gated to `[after, before]` via the existing
`date.isBefore(after)` guards in `collectEntrySignals` and `buildTradingDateRange`. No warmup bar can
become a trade. Over-loading is free (read-only history); under-loading silently re-starves the ranker,
so the engine biases to over-load and logs a tripwire when a trailing ranker still leaves
>5% of in-window entries unscoreable.

Using pre-`startDate` bars for ranking — e.g. 1998–2000 data to rank a 2000-window entry in firewall
Block A — is **forward-clean, not leakage**: every warmup bar strictly precedes the entry it informs.
(The "breadth trust floor = 2000-01-01" memory concerns *breadth* survivorship tilt, not raw OHLCV; the
residual rankers use raw stock/SPY/sector returns, so that floor does not bind them.)

## Consequences

- Trailing-history rankers are now validly screenable; their OOS selection reflects real skill, not RNG.
- The #130 `MarketResidualMomentum` screen verdict is **void** and must be re-run on the fixed engine
  before the single-factor residual-momentum recipe can be crossed off. No *passing* candidate needs
  re-validation: none uses a trailing-history ranker, and sizers consume the precomputed scalar ATR
  (verified), so no sized equity curve shifts.
- Each window load now also fetches the warmup span (and, for the multi-factor ranker, 9 sector-ETF
  series). The extra cost is read-only history over already-indexed columns; acceptable at funnel scale.
- The fix is ranker-driven: 0-warmup rankers (the common case) load exactly from `after` as before, so
  existing verdicts that used precomputed-indicator rankers are byte-identical.
