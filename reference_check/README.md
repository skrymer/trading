# reference_check

Independent Python cross-validators for Midgaard/Udgaard calculations. Local diagnostic only — not wired into CI.

## Setup

Stdlib + `requests` (already present on most systems; on Ubuntu `apt install python3-requests`):

```bash
cd reference_check
python3 ema_donchian_diff.py --help
```

## Scripts

### `ema_donchian_diff.py`

Recomputes EMA (5/10/20/50/100/200) and Donchian upper 5 from raw OHLCV pulled from Midgaard `/api/quotes/bulk`, diffs against Midgaard's stored values.

```bash
python ema_donchian_diff.py                          # defaults to localhost:8081, 2016-2025, 51-symbol sample
python ema_donchian_diff.py --base-url http://localhost:8081 --verbose
python ema_donchian_diff.py --symbols SPY XLK AAPL   # override symbol list
```

Exits 0 when every indicator's max-abs-diff is ≤ 1e-4 (Midgaard stores at 4-decimal precision), non-zero otherwise.

### Intentional-break sanity check

To verify the harness actually discriminates, temporarily change `midgaard/.../IndicatorCalculator.kt` — e.g. `multiplier = 2.0 / (period + 1)` → `2.0 / period` — re-run Midgaard ingest, then run the harness. It must fail.

### `eodhd_alphavantage_diff.py`

Independent cross-check of EODHD vs AlphaVantage on a sample of mega-cap symbols. Diffs adjusted_close (1e-3 tolerance), ATR (1% tolerance), and ADX (1% tolerance). Run before promoting EODHD to default ingest source.

```bash
python3 eodhd_alphavantage_diff.py --eodhd-key <key> --av-key <key>
python3 eodhd_alphavantage_diff.py --eodhd-key <key> --av-key <key> --symbols AAPL TSLA --days 50
```

Exits 0 when every series is within tolerance, non-zero otherwise. ATR/ADX disagreement above 1% usually points to a Wilder vs RMA smoothing difference — investigate before flipping `app.ingest.provider=eodhd`.

### `vcp_condition_verifier.py`

Per-trade re-verifier for VCP entry conditions. For every trade the engine took, independently re-evaluates 7 of the 8 VCP entry conditions on the signal day using raw Midgaard quote data and Udgaard breadth endpoints.

```bash
python3 vcp_condition_verifier.py --start-date 2023-01-01 --end-date 2023-12-31
python3 vcp_condition_verifier.py --backtest-id <uuid> --verbose     # reuse an in-memory backtest
```

Conditions verified (all 8): `minimumPrice`, `uptrend`, `priceNearDonchianHigh`, `volumeAboveAverage`, `volatilityContracted`, `marketUptrend`, `sectorUptrend`, `aboveBearishOrderBlock`.

The `aboveBearishOrderBlock` check takes a shortcut that is safe **only** for the VCP config (`consecutiveDays=1, ageInDays=0`): with `consecutiveDays=1` the TradingView-style barsSince walkback never binds, so the predicate reduces to "close not inside and not within 2% below any bearish OB that was triggered on or before the signal day and whose stored `endDate` is null or on/after the signal day". If the condition's parameters ever change, that shortcut needs to be widened — the script errors out on `ageInDays != 0`.

Important semantics the script mirrors from the engine:

- **Signal day ≠ entry day** when `entryDelayDays > 0` (VCP uses 1). The engine evaluates conditions on the signal day and places the entry on the next trading day. The script walks back `--entry-delay-days` trading days from the trade's `entryQuote.date` to find the signal day.
- **No pre-backtest warmup.** `BacktestService` loads `stock.quotes` with `quotesAfter = startDate` — rolling averages (volume, volatility) don't see pre-`startDate` bars. The script clips its Midgaard data the same way so condition inputs match. Consequence: volume lookback falls below 20 bars during the first ~10 trading days of any backtest, matching the engine's cold-start behavior.

Exits 0 when every trade passes every verified condition, non-zero otherwise.

### Regime read-out v3 trend-efficiency acceptance (ADR 0028)

Operator-run validation of the v3 trend-efficiency overlay (issue #180) — run once, after the
pre-registration freeze, against a udgaard serving `version=V3`. The trio is descriptive evidence for
the trader's report; none of them promotes a label to gateable.

```bash
python3 regime_readout_anchor_check.py --version V3 --base-url http://localhost:8080/udgaard   # 1. anchors
python3 regime_readout_rw_surrogate.py --base-url http://localhost:8080/udgaard                # 2. RW surrogate
python3 regime_readout_frozen_oos.py   --base-url http://localhost:8080/udgaard                # 3. frozen-OOS
```

- **`regime_readout_anchor_check.py --version V3`** — the primary acceptance check. Verifies GRIND and
  CHOP each clear 60% on their frozen anchor spans WITHOUT regressing the byte-identical CRISIS/THRUST
  backbone, plus stability and the unchanged CHOP D-FLAT ≥70%. Also prints the report-only
  GRIND-demotion rate (a near-zero rate is a recordable "axis inert" negative result) and the per-spell
  Choppiness band histogram. Without `--version` it runs the frozen v2 production check unchanged.
- **`regime_readout_rw_surrogate.py`** — does the Choppiness axis separate GRIND from CHOP beyond a
  calibrated random walk? Stationary block bootstrap (L=5, K=1000, frozen seed) of SPY's
  (log-return, high/close, low/close) bars; passes when the real GRIND-vs-CHOP Choppiness gap exceeds
  the surrogate p95 (one-sided p < 0.05).
- **`regime_readout_frozen_oos.py`** — efficiency-axis stationarity across the frozen 2014-12-31 cutoff
  (GRIND-survival-rate + surviving-vs-demoted Choppiness, IS vs OOS). Binds only on the already-frozen
  stability floors OOS; the IS-vs-OOS stationarity reads are reported for operator/quant judgment, not
  auto-gated on an invented tolerance.

A failed anchor or surrogate is a **recorded negative result, never a retry** at another window or
threshold (ADR 0028 one-shot / no-sweep).
