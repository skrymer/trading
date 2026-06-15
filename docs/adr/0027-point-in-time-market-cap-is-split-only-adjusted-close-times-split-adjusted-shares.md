# Point-in-time market cap = split-only-adjusted close × split-adjusted shares — the stored close is split-AND-dividend adjusted, shares are split-only, so the naive product carries a dividend bias

The regime read-out v3 NARROW axis (#168) needs a **point-in-time market cap** per name back to the 2000 trust floor, to build a cap-weighted top-N return-concentration signal (mega-cap concentration). The cap primitive is delivered by #174 as a `Stock.marketCapAsOf(date)` accessor (ADR-0019 pattern). The obvious construct — `stored_close(t) × sharesOutstanding(t)` — is **wrong**, in a way that silently corrupts exactly the signal it feeds. This ADR records the correct construct, the algebra behind it, and an undocumented data-semantics landmine it exposed.

## Verified data facts (real AAPL probe, EODHD, 2026-06-15)

- Stored `close_price` (Midgaard `EodhdHistoricalBar.toRawBar` → persists `close = adjusted_close`; the raw `close` field is in the payload but **dropped**) = EODHD `adjusted_close` = **split AND dividend adjusted**.
- EODHD `commonStockSharesOutstanding` (Balance_Sheet quarterly) **and** the dedicated `outstandingShares` section are both **split-adjusted (current-basis), identical values** — NOT as-reported. (AAPL 2020-06-30 = ~17.42B = as-reported 4.33B × 4, back-adjusted through the Aug-2020 4:1 split.) Dividends do not adjust share counts.
- Raw/as-reported shares are **not** readily available from EODHD; the raw daily close **is** (re-storable).
- Coverage is deep: Midgaard `datastore.fundamentals` spans 1983–2026, majors to 1985 (well past the 2000 floor).
- Float-precision artifact: EODHD returns e.g. `17113687999999998` for ~17.11B shares.

## The algebra (why the naive product is wrong)

Anchor to ground truth `trueCap(t) = raw_close(t) × shares_raw(t)`. Let `k(t)` = cumulative split factor from `t` to today (splits move price and shares inversely → cap-invariant), `d(t) ≤ 1` = cumulative dividend back-adjustment. EODHD gives `adjClose = raw_close · d/k` and `shares_adj = shares_raw · k`:

| Construct | Product | Result |
|---|---|---|
| (d) naive `adjClose × shares_adj` | `trueCap · d(t)` | split cancels; **residual dividend bias** |
| (b) `raw_close × shares_adj` | `trueCap · k(t)` | **re-introduces the split error** (NVDA 40×, AAPL 28×) |
| (a) `raw_close × shares_raw` | `trueCap` (exact) | blocked — raw shares unavailable |
| (c) `(raw_close/k) × shares_adj` | `trueCap` (exact) | **correct and buildable** |

The naive (d) bias `d(t)` is per-name, multiplicative, and monotone in lookback — ~1–5% on low-dividend mega-caps (the NARROW targets) but **40–60% on high-yield names**. Because concentration is a *ratio* (low-div numerator over a whole contaminated by deflated high-div names), it **inflates ~25–50% toward the 2000 floor** — a spurious "the past was more narrow" secular drift that would mis-calibrate a pre-registered, fixed-threshold, strategy-blind regime axis. That is disqualifying, not cosmetic.

## Decision

1. **Cap construct (c):** `marketCap(t) = (raw_close(t) / k(t)) × sharesOutstanding_adj(latest fundamental visible as of t)`, where `k(t)` is the cumulative split factor (product of split ratios with ex-date > t).
2. **Point-in-time** via the existing ADR-0019 filing-date gating (`Fundamental.isVisibleAsOf` / `Stock.latestFundamentalAsOf`) — shares become visible only at `filing_date`. No new lookahead surface.
3. A **`Stock.marketCapAsOf(date)` accessor** (in-memory, ADR-0019 pattern), not a repository. A universe-wide cross-section repository is deferred to the consumer (#168) and built only if its single-pass cross-section needs it.
4. **Build requirements:** (a) extract the split-adjusted `commonStockSharesOutstanding` from the Balance_Sheet quarterly already fetched (new field on the model + a column on Midgaard `datastore.fundamentals` and Udgaard `trading.fundamentals`); (b) **re-store the raw close** (a new bars column — it's in the payload, currently dropped); (c) a **new EODHD splits client + splits table** to compute `k(t)`; (d) the accessor. Store shares as `BigDecimal`/`Long` (handle the float artifact). Provider specifics stay in the EODHD client; the domain model + accessor stay provider-neutral.
5. **Scope:** this primitive only. Consumers own their cap-derived predicates — the **#168** NARROW concentration axis (top-N-by-cap universe + cap-weighted return contribution) and the **#173 / ADR-0026** `$300M` tradable cap floor (now built and active — fail-open on a null cap). The top-N-by-cap universe must inherit the ADR-0011 point-in-time STOCK-or-null + survivorship properties (delisted names keep bars; respect the 2000 floor) so the early-era top-N is not survivor-tilted.

## Consequences

- The minimal "just extract the shares we already fetch" path is **rejected** — it ships construct (d)'s dividend drift. Correctness costs the raw-close re-store (trivial) + one splits endpoint/table (modest, build-once). There is no cheap middle: a market-wide dividend approximation cannot fix a *cross-sectional* (per-name) bias.
- Using raw close is also strictly **more precise** for deep-past bars than the cumulatively-back-adjusted `adjusted_close`; `k(t)` is a product of exact integer ratios, introducing no drift.
- The existing `~310k` fundamentals rows + bars lack shares/raw-close; populating them is an operator-triggered re-ingestion (ADR 0019 cadence), not an automatic backfill.

## Landmine recorded (the reason this investigation was needed)

**`close_price` is EODHD `adjusted_close` = split AND dividend adjusted, and this was undocumented.** Any consumer that multiplies `close_price` by a share count, or uses it for an absolute-level (non-return) computation, silently inherits the dividend adjustment. Returns and ratios are unaffected (the factor cancels); absolute-level products are not. New absolute-level consumers must use the raw close (this ADR's column), not `close_price`.

## Alternatives considered

- **(d) `adjClose × shares_adj`** (the issue's original "extract shares" path) — rejected: dividend drift, disqualifying for a pre-registered regime threshold.
- **(b) `raw_close × shares_adj`** — rejected: re-introduces the split factor (catastrophic on the heavily-split mega-caps the signal is about).
- **(a) `raw_close × shares_raw`** — the textbook cap, rejected as un-buildable: EODHD does not expose as-reported shares (only split-adjusted).
- **EODHD direct historical market-cap endpoint** — rejected earlier (#174): 2020+ and weekly, fails the 2000 floor.
- **Extend ADR 0019 instead of a new ADR** — rejected: the cap construct + the `close_price` semantics landmine are a distinct, surprising, hard-to-reverse decision warranting its own record; it cites ADR 0019 for the point-in-time pattern.
