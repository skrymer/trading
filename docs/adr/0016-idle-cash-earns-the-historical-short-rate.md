# Idle cash earns the historical short rate

The backtest engine credited idle (uninvested) cash **0%**; reality is the short T-bill rate (~3% recently, where the operator parks cash in SGOV). For a part-in-cash long-only strategy this understated CAGR/Calmar in proportion to time-in-cash — biting hardest on heavy-cash years. This ADR adds idle-cash interest at the **historical** short rate, wired so it is **Sharpe-neutral and Calmar-additive** by construction. Deferred from ADR 0013; resolves deep-research audit follow-up; issue #103.

## The accrual base

Interest accrues on `idle = max(0.0, state.cash − state.openNotional)`, **not** on `state.cash`.

The engine's cash model carries deployed capital at **cost basis** inside `state.cash` (purchases are not debited; daily value = `cash + unrealizedPnl`). So `state.cash` is the whole capital basis, not the uninvested balance. The decomposition `PV = openNotional + unrealizedPnl + idle = (market value of holdings) + idle` shows the deployed dollars already earn the equity return via `unrealizedPnl`; only `idle` should earn interest. **The subtrahend is cost basis (`openNotional = Σ shares·entryPrice`), never market value** — `state.cash` carries cost basis, so subtracting market value would double-remove the MTM gain. Realized P/L sitting in cash correctly earns interest (it is real uninvested cash). Crediting full `state.cash` would pay interest on capital that is simultaneously earning the stock return — a double-count.

**Leverage:** the `max(0, …)` clamp is the v1 handling. With `leverageRatio > 1`, `openNotional` can exceed `cash` → negative idle = a margin borrow; crediting it would pay you to borrow (a bug). v1 credits positive idle only and does **not** charge margin-borrow cost (consistent with the engine's uniform friction omission — no slippage/commission either). Levered results carry a small upward bias, flagged where leverage is reported; borrow-cost is a separately-scoped future feature.

## The rate: historical, not flat

The rate is the **historical daily 3-month T-bill yield** (the series SGOV tracks), spanning 2000-2025 — **not a flat constant**. A flat ~3% would over-credit the long ZIRP era (2009-2015, 2020-2021 ≈ 0%) by ~3%/yr, flattering exactly the cash-heavy timers the firewall must validate honestly; the error is regime-correlated (a timer is cash-heavy precisely when the Fed cut to zero), so it does not average out. SGOV itself only exists from 2020, so it cannot be the 25-year source — the T-bill yield series it tracks is the full-span proxy. **Sourced via EODHD** (the active provider — `US3M.GBOND`-style gov-bond yield), ingested + stored + served as Midgaard reference data, loaded by `BacktestService` like SPY.

**Expense is subtracted exactly once.** Midgaard stores the **gross** T-bill yield (a clean reference rate); Udgaard subtracts the ~0.10% SGOV expense **once**, via the config constant, to get the net idle rate. Do *not* also net it into the stored series — that would double-haircut to ~0.20%.

**The net rate is floored at 0** (`max(0, gross − expense)`). In deep-ZIRP windows (2009–2015 patches, 2020–2021) the gross 3-month yield dipped below the ~0.10% expense, which would otherwise make the net rate slightly negative and *debit* idle cash. That is a modeling artifact, not reality: SGOV waived fees in ZIRP to keep its net NAV flat (~0%, never a steady negative carry), and a rational operator holds plain cash at 0% rather than a vehicle with known negative net carry. The floor lives in the single `rf_step(t)` source of truth, so the same floored value is credited to cash *and* subtracted in Sharpe/Sortino — the Sharpe-neutrality cancellation holds for the floored value (the invariant is "the identical value on both legs," not "gross − expense specifically"). Quant-adjudicated; magnitude is immaterial to the G9/G15 gates (Sharpe is neutral to the credit; the CAGR/Calmar effect is sub-basis-point), but the sign is correct — flooring removes a small anti-bias that would otherwise penalize exactly the cash-heavy ZIRP regime this feature exists to model honestly.

**Rate alignment (no look-ahead).** The rate effective at step `t` is the most-recent yield **published on-or-before `t`** — never `rf(t+1)` or a forward-filled future print. The 3-month T-bill yield for a date is known at that date's open, so same-day crediting is not look-ahead.

## Day-count and the single `rf_step(t)` invariant

The equity curve becomes a **full daily spine** — `PositionSizingService` iterates every trading day from first to last activity (using a trading-day calendar, e.g. SPY's quote dates), recording an equity point each day with interest accrued. This is the "(a) full spine" option (not lazy-lump, which would concentrate a flat gap's interest into one spurious return spike, nor gap-backfill) — it also sharpens drawdown and the ADR-0013 SPY-baseline stitching.

**Idle accrues only within OOS spans, never across stitched IS gaps.** In a walk-forward, the stitched-OOS curve omits the in-sample periods between windows (ADR 0005) — the strategy has no equity path there. Idle interest must inherit that exclusion: crediting across a multi-month IS gap would manufacture phantom interest on capital the stitched curve doesn't hold on those dates. The daily spine runs within each window's span; the stitch never accrues over the gaps.

Accrual is **calendar-day, ACT/360**: each step credits `idle · rf_annual(t) · (calendar_days_since_prev_step / 360)` — cash earns 7 days/week (Fri→Mon = 3 days); dropping weekends would be a ~29% haircut on cash interest. 360-vs-365 is negligible (~single bps/yr); calendar-vs-trading-day is the material choice. Interest compounds (credited into cash daily).

**The one engine invariant:** compute `rf_step(t)` **once** and feed the *identical* value to both (a) the cash credit and (b) the rf subtracted in Sharpe/Sortino. A single source-of-truth is what keeps the coherence below true as the code evolves.

## Why this is Sharpe-neutral and Calmar-additive

Decompose the blended daily return `r_p(t) = w_dep·r_dep(t) + w_idle·rf_step(t)`. Subtracting the **same** `rf_step(t)` in the Sharpe excess return:

```
r_p(t) − rf_step(t) = w_dep·(r_dep − rf_step)
```

The idle leg nets to **zero excess** (risk-free cash has no risk-adjusted edge), leaving only the risky leg's excess — exactly what Sharpe should reward. So **Sharpe/Sortino are neutral to idle-cash crediting** (given the aligned series), and `RiskMetricsService`'s scalar `riskFreeRatePct` becomes the per-day `rf_step(t)` provider (with a `≡ 0` raw-Sharpe escape). The cancellation is exact *per step, before* the `sqrt(252)` ratio annualization — the 360 day-count and the 252 annualization live at different layers and need not agree; the only constraint is credit-day-count ≡ rf-day-count.

The dangerous combination to avoid: idle cash earning the rate while Sharpe keeps `rf = 0` — raw Sharpe then miscredits the T-bill coupon as alpha (~0.07-0.10 inflation on a 50%-cash book, on the numerator with ~zero vol impact), **loosening the G9 Sharpe ≥ 0.5 gate** — strictly worse than the pre-feature status quo. The aligned-series wiring is mandatory.

**CAGR/Calmar take no rf subtraction**, so crediting idle cash **raises CAGR → raises Calmar** — correct and intended: a book that holds its powder in T-bills genuinely earns the coupon at no incremental drawdown; the old 0%-cash model understated cash-heavy strategies' Calmar.

**The SPY baseline (ADR 0013) earns zero idle credit — by construction, and this is the correct asymmetry.** SPY buy-and-hold is always 100% invested, so its `idle = max(0, cash − openNotional) ≡ 0` every day — there is no uninvested balance to accrue on. The credit is therefore *one-sided*: it lifts a cash-heavy strategy's Calmar but not SPY's, widening the relative advantage in proportion to time-in-cash. That is the **real economic difference** between the two books (the timer earned the coupon; SPY was exposed to equity drawdown the whole time) — exactly what the relative gate measures, so it stays apples-to-apples. **Implementation invariant + regression test:** the shared spine/credit code path must produce `SPY idle-credit = 0` on every date. If the SPY harness ever carries an uninvested residual (rounding, fractional-share remainder, between-reinvestment dividend cash) and silently credits it, it would *narrow* the relative gate and create a false-FAIL — so `SPY idle ≡ 0` must be asserted, not assumed.

## Default and gate interaction

- **Default ON** (`creditIdleCash = true`), consistent with `costBps` net-by-default and the engine-simulates-reality principle. **Loud 0% fallback** if the rf series is missing for a span (never silently credit a wrong rate). **Data-first sequencing**: Midgaard must serve the series before default-on is meaningful. `creditIdleCash = false` reproduces old 0%-*credit* results — but **not** pre-feature baselines bit-for-bit, because the **daily spine is independent of the credit flag** (it is the right curve and also sharpens ADR-0013 stitching) and shifts Sharpe/Calmar slightly even at 0% credit.
- **ADR-0015 gates:** G9 Sharpe ≥ 0.5 is **unaffected by the credit** (Sharpe-neutral by construction — this *supersedes* ADR 0015's "G9's 0.5 may be revisited upward once it lands"; the aligned-rf wiring removes the drag that note anticipated, so no upward revisit is warranted). G15 Calmar ≥ 1.5 and the ADR-0013 relative Calmar bar get **modestly easier for cash-heavy candidates** once crediting lands — economically correct (real coupon, no extra drawdown), so **no threshold change**; the bars were set on the 0%-cash engine and the calibrators should know.
- **The spine shift must be measured, not assumed.** Before this is treated as settled, run one representative candidate through the pre-spine and post-spine engine and confirm the Sharpe/Calmar delta (at 0% credit) is small and same-signed for strategy and SPY — if so, the *relative* gate is undisturbed and the *absolute* G9/G15 floors retain margin. If the delta is material, this escalates from a documentation note to a **G9/G15 recalibration pass**. This is an open verification item for the implementation, not an asserted fact.

## Consequences

- Spans **Midgaard** (ingest + store + serve the EODHD treasury-yield series) and **Udgaard** (`PositionSizingService` daily spine + credit; `RiskMetricsService.sharpe/sortino` per-day rf via the shared `rf_step(t)`; `dailyReturns()` must carry dates / a parallel rf series; `BacktestService` loads the series and fans the provider). `cagr()`/`calmar()` unchanged.
- Config: `creditIdleCash` (+ expense constant ~0.10%, subtracted once — F4) on `StrategyConfigDto → BacktestContext`, because the series must reach both the sizing pass and the risk-metrics pass — not a `PositionSizingConfig`-only field.
- **Monte Carlo** resamples the credit-inclusive blended return series, so resampled paths carry the idle-coupon structure coherently. Minor caveat: block-bootstrap over idle-heavy flat-day runs slightly lowers resampled path variance — diagnostic-only (MC is not a gate), worth awareness, not a fix.

## What this does NOT decide

- **Margin-borrow cost** on negative idle (levered books) — deferred, separately scoped.
- **Re-confirming G15's 1.5 on the credited engine** — left as a known, documented shift, not a recalibration loop.
