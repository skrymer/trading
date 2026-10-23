# Cut the data layer over to Sharadar and rebuild from empty — a table-oriented feed, a split-adjusted signal price with a forward-built total-return index, and every fundamental filing vintage

EODHD is cancelled and its data is untrusted: zombie price series that kept printing years past a real delisting, corrupt dividend and split factors, delisted names registered in Midgaard's `symbols` table with **zero** price bars (every backtest to date saw survivors only), and a fundamentals feed with no filing vintages. The sister project skrymerBT ran a full dual-run of EODHD against **Sharadar (Nasdaq Data Link, SFA bundle)** and every divergence resolved in Sharadar's favour (skrymerBT ADR 0080). We cut Midgaard over to Sharadar and **rebuild both data stores from empty**, discarding every prior result. This is a *data epoch*.

## Decision

1. **Ingest the candidate set, not a selected list.** Midgaard ingests every US common stock Sharadar `TICKERS` carries with prices since the 2000 trust floor, using skrymerBT ADR 0075's category filter: the category contains "Common Stock" and is not a Warrant. That is about 18.3k names, about 12.4k of them delisted. Funds (SPY, the sector SPDRs, RSP, and the bond, commodity and leveraged ETFs) come from `SFP`. The measurement universe is the whole candidate set. The backtest universe is a subset of it, defined in ADR 0030.
2. **Identity: key on the ticker, store the entity id.** The Sharadar ticker is the key. Live names stay bare; a retired entity whose ticker was reused carries Sharadar's numeric suffix (`AAC1`). `permaticker` is stored as the entity id. A rename re-keys the entity's whole history to the new ticker. Portfolio and scanner records keep the ticker that was traded at the time. Shares and fundamentals hang off the entity's own ticker, which defuses ADR 0026's ticker-reuse landmine by construction.
3. **Price basis.**
   - **Stored signal price:** SEP's split-adjusted OHLCV, never dividend-adjusted. All indicators, conditions, rankers and the price-only RS percentile read it.
   - **`rawClose`:** Sharadar's `closeunadj`. It is the only price an absolute-level rule may read: the `$5` floor, price-level conditions, and (÷ k(t)) the ADR 0027 market cap. Dollar volume is signal price × split-adjusted volume, which equals raw dollar volume. Never `rawClose` × split-adjusted volume, which mixes bases.
   - **`trIndex`:** a total-return index built forward on the **split-adjusted** basis (`close` = the stored split-adjusted close; `div_t` from `ACTIONS`, split-adjusted to the same basis) with `TR_t = TR_{t−1} × (close_t + div_t) / close_{t−1}`. It is never re-based from a vendor's back-adjusted close.
   - **P&L reads total return.** Strategy P&L **and the SPY buy-and-hold baseline** read `trIndex`, so strategy and benchmark are both total-return and ADR 0013's SPY-Calmar gate is unbiased.
   - **Only a split rewrites history.** That one ticker is re-pulled whole, and its indicators and `trIndex` are recomputed; every other day is a pure append. A dividend-adjusted stored price (the EODHD-era contract) rewrites history at every ex-dividend date and silently breaks the append seam.
4. **Fix the tradable floor.** `TradableUniverseFilter`'s `$5` leg reads `rawClose`. It previously read the back-adjusted close, so heavily split names were wrongly excluded in the past, which is look-ahead. A missing `rawClose` is a data error, not a fallback.
5. **Fundamentals keep every filing vintage.** SF1 `ARQ` (as-filed) is the only dimension stored. A restated quarter is an additional row under its later `datekey` (the exact SEC filing date). The key is `(symbol, fiscal_date_ending, filing_date)`. Consumers resolve each fiscal period to its **latest vintage with `filing_date ≤ D`** before any TTM sum. This removes the restatement leak previously accepted under *Point-in-time fundamentals*. `sharesbas` is split-adjusted to the current basis (verified on AAPL's 2020 split), so ADR 0027's market cap is unchanged.
6. **A table-oriented feed replaces the per-symbol vendor interfaces.**
   - `OhlcvProvider`, `SplitsProvider`, `FundamentalsProvider` and `CompanyInfoProvider` collapse into one domain-typed `ReferenceDataFeed`. It offers `securities()`, plus `bars`, `corporateActions` and `fundamentals` (each taking an opaque `since` watermark), plus single-ticker `barsFor` for repairs.
   - The initial load is one bulk export per table, streamed into Postgres. The daily update is one watermark-filtered pull per table. Watermarks are per table and advance only after a fully successful run (skrymerBT ADR 0078).
   - `IndicatorProvider` and `EarningsProvider` are deleted (indicators are always computed locally; earnings are dropped). So are the `app.ingest.provider` toggle and the EODHD and AlphaVantage OHLCV paths.
   - Live quotes (Finnhub), options (AlphaVantage) and FX stay per-symbol interfaces.
   - Sharadar's names (`SEP`/`SFP` routing, `closeunadj`, `permaticker`) stay inside the adapter.
   - This deliberately departs from ADR 0003's "catalogs stay vendor-specific": the security master *is* the candidate set, so it is normalised to domain terms.
7. **Remaining EODHD feeds.** Treasury yields come from FRED `DGS3MO` (the 3-month T-bill yield ADR 0016 specifies, now from a new source; constant-maturity basis). FX comes from ECB reference rates. Earnings are **dropped**. SF1's `datekey` is a filing date, not an announcement date, so it cannot stand in. `NoEarningsWithinDays` and `DaysSinceEarnings` remain and always fail until a premise justifies a new earnings source.
8. **Sectors.** Sharadar's `sector` field uses exactly the 11 names `SectorMapping` already maps to the SPDRs. About 1% of names have no sector and are left unclassified. `SectorNormalizer`, the EDGAR SIC fallback and `DelistedIngestionService` are deleted.
9. **Rebuild from empty.** Both the Midgaard `datastore` and the Udgaard `trading` database are wiped, including the portfolio, scanner history, backtest reports and settings. The portfolio is re-synced from IBKR. Flyway history is kept; a migration empties the tables.
10. **Data-calibrated constants are re-derived once, by their frozen procedure.**
    - **Kept unchanged:** constants set from real-world or literature reasoning, i.e. the ADR 0026 floors, the 2000 trust floor, `GP_TA_CEILING`, the −20%/252-bar CRISIS leg, the v3 Choppiness bands and design horizons.
    - **Re-derived:** constants measured on the old data, by re-running their pre-registered procedure on Sharadar data. That covers the v2 median-gap p33/p67 Block-A terciles (ADR 0024's procedure).
    - **Ambiguous constants:** the breadth bands 50/35, the washout 15/10/40, the vol bands 0.12/0.22, and leadership `maxTrustworthyStandardError`. Each keeps its value if, on Sharadar data, it lies within a pre-registered tolerance of the percentile it occupied on the old data; otherwise it is reset to that percentile's value on the new data. The rule and the tolerance are pre-registered before any coverage is computed.
    - The v2 anchor adjudication (ADR 0024) is re-run. This is a one-time, candidate-blind re-anchor, not tuning.
11. **Acceptance gate.** Research resumes only when both checks below pass. The report is filed in the wiki as the epoch record.
    - **Midgaard integrity is green.** The `DataIntegrityValidator` set is extended with coverage, zombie-bar, fundamentals and reference-point validators, `BadPrintIntegrityValidator` V2/V3 cross-checked against `ACTIONS`, and `SectorIntegrityValidator` I4 dropped and I5 relaxed. Severity gains one behavioural rule: accepted means **zero CRITICAL/HIGH violations**, exposed as a readiness verdict.
    - **The Udgaard readiness check is green:** breadth and the regime constants have been rebuilt from the current data.

## Consequences

- **Data epoch** (CONTEXT.md): every prior verdict is `SUPERSEDED-BY-DATA-EPOCH`. The dead-config brake is **not** reset. Re-firing a dead config needs a recorded structural-interaction judgment that the epoch plausibly caused its death (e.g. an edge that lived in a bad-data or illiquid tail), and every re-fire counts toward `N_eff`. Mechanistic deaths earn no re-run.
- Backtest P&L now includes dividends and is comparable with a total-return SPY. Price-only RS ranks high yielders a few percentiles lower; this is documented and accepted.
- Any condition that compares against an absolute level (share-volume floors, dollar ATR, price-level rules) must read `rawClose` or dollar volume. These are to be audited.
- PRD's EODHD-backed refreshes are already dead. PRD is rebuilt rather than migrated.

## Considered options

- **Keep EODHD for earnings only.** Rejected: EODHD is already cancelled, and no live premise uses earnings.
- **A per-symbol `SharadarProvider` behind today's interfaces.** Rejected: about 55k calls for the initial load and about 18k a day, which fights the vendor's table-shaped API.
- **A dividend-adjusted stored price.** Rejected: every ex-dividend date rewrites history (thousands of tickers a quarter), and signals would run on prices nobody sees on a chart.
- **Plain split-adjusted price with no total-return index.** Rejected: it understates P&L by roughly the dividend yield, about the size of the whole cost model, tilts against high yielders, and turns the SPY baseline price-only, which loosens the SPY-Calmar gate.
- **Keep only the first-filed vintage of fundamentals.** Rejected: it never sees corrections. Keeping every vintage is true point-in-time.
- **Migrate in place with an EODHD→Sharadar ticker crosswalk.** Rejected: skrymerBT needed 329 lines for it, and nothing in the old stores is trusted.
