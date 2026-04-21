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
