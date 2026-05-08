# Plan: ScannerService deepening (candidate #3) — Phase 1: coverage build-up

Status: **✅ Phase 1 completed (PRs #14, #15, #16, 2026-05-08).** Phase 2 (split into `ScannerEvaluator` / `ScannerTradeStore` / `ScannerStatsService`) ready to start; deferred until next scanner change. See [Phase 2 readiness check](#phase-2-readiness-check-2026-05-09) below for the decision-marker analysis. See [deepening-candidates.md](deepening-candidates.md) for the full list.

## Smell

`udgaard/.../scanner/service/ScannerService.kt` is **940 lines** in a single class wearing 5 distinct hats: scan-for-entries, check-exits-on-trades, validate-entries-against-live-quotes, trade-lifecycle CRUD (open/close/roll/delete), and closed-trade analytics (drawdown, edge, win rate). All methods share one Spring singleton with 10 constructor deps; tests for any one hat have to load the entire mock graph.

The roadmap entry already called the risk: "Subtle behaviour changes — needs solid scanner test coverage first (currently thin)." This plan does **not** propose the split. It builds the coverage foundation that lets us evaluate the split safely in Phase 2.

## Behaviour corrections in scope

Per [`feedback_dont_pin_broken_behaviour`](../../home/skrymer/.claude/projects/-home-skrymer-Development-git-trading/memory/feedback_dont_pin_broken_behaviour.md), the audit surfaced patterns that are **buggy or anti-patterns** — fix them alongside the tests rather than pinning them. Resolved via grilling on 2026-05-08:

| Issue | Fix |
|---|---|
| `findOpenTrade` throws `IllegalArgumentException` for both not-found and already-closed; `ScannerController` distinguishes via brittle string-match on `"already closed"` | Throw `NoSuchElementException` for both cases (message differentiates). `GlobalExceptionHandler` already maps to **404**. Drop the controller try/catch. **404 collapse: no 409 distinction** — single-user app, asgaard reloads either way, and the error message carries the actual info. |
| PnL formula duplicated 4× (`closeTrade`, `tradePnlPercent`, test helper, `calculatePnlDollars`) | Per [ADR 0001](../adr/0001-rich-domain-objects.md), lift to `ScannerTrade.realisedPnl(exitPrice): Double` — single source of truth. Companion factory + copy methods on `ScannerTrade`: `withClosed(exitDate, exitPrice, now)`, `withNotes(notes)`. Service methods become 1–2 line orchestrations. |
| `validateEntries` silently caps to `MAX_VALIDATE_SYMBOLS = 30` — no signal in the response | Reject with **HTTP 400** if `request.symbols.size > 30`. `require(...)` at the boundary; `IllegalArgumentException` already maps to 400 via `GlobalExceptionHandler`. Forces caller to chunk explicitly. |
| `updateTrade` / `deleteTrade` are 2-call sequences (`findById` + `save`/`delete`) without `@Transactional` | Add `@Transactional` to both. One line per method. Brings them in line with `closeTrade`/`rollTrade`/`deleteAllTrades`. |
| `checkExits returns empty response when no trades exist` test stubs `findAll` instead of `findOpen` | Trivial test fix in PR 0. |

**Deferred (not in Phase 1):**

| Issue | Reason |
|---|---|
| `tradingDaysHeld` N+1 enrichment in `getTrades` | Single-user scale → ~500ms worst case. Real fix requires restructuring the data fetch (~20 lines). Add `// TODO N+1` comment so it's visible if it ever surfaces in profiling. |
| `POST /api/scanner/option-contracts` placement | Architectural decision orthogonal to coverage. Likely belongs in `OptionController` or a new `OptionContractRecommendationService`. Phase 1 still adds an E2E that covers behaviour at the current location; the test will move with the endpoint when the placement is decided. |

## Audit findings — existing coverage today

### Test files under `udgaard/src/test/kotlin/com/skrymer/udgaard/scanner/`

Only one file:

- `udgaard/src/test/kotlin/com/skrymer/udgaard/scanner/service/ScannerServiceTest.kt` (1659 lines, 41 `@Test` cases, all mock-based — same shape as `BrokerIntegrationServiceTest`, the canonical orchestration-service test pattern in this codebase).

There are **no** `scanner/controller/` tests, **no** `scanner/repository/` tests, and **no** `scanner/mapper/` tests.

### E2E tests touching scanner endpoints

`grep -rn "scanner\|/api/scanner"` over `udgaard/src/test/kotlin/com/skrymer/udgaard/e2e/` returns **zero matches**. Every other domain (backtest, portfolio, IBKR import, walk-forward, monte-carlo, cash transactions, forex tracking) has at least one `*E2ETest`; **scanner has none**. This is the largest single coverage hole.

### Per-method coverage inventory — `ScannerService` public API

| Public method | Covering test class(es) | Verdict | Failure mode that would slip through |
|---|---|---|---|
| `scan(ScanRequest)` | `ScannerServiceTest` — 2 cases (happy, filter) | **SHALLOW** | Ranker selection (request override vs strategy preference vs RandomRanker fallback), near-miss limit enforcement, condition-failure-summary aggregation, sector include/exclude filters, asset-type filter, `currentMarketDate` quote-date guard, `DetailedEntryStrategy` vs plain branch on the *scan* path, the `BATCH_SIZE = 150` chunked concurrent path — all silently ignored. |
| `checkExits()` | `ScannerServiceTest` — 14 cases covering proximity sort, synthetic-quote EMA/ATR/Donchian projection, same-bar guard (4 boundary tests), append-when-newer invariant, live-quote-date stamping, zero-EMA preservation, concurrent multi-symbol fetch, no-trades empty path | **SOLID** | Strong. Synthetic-quote machinery is the most rigorously tested area in the file. **Note:** `checkExits returns empty response when no trades exist` stubs `findAll` instead of `findOpen` (code calls `findOpen`); cosmetic, fix in Phase 1. |
| `validateEntries(ValidateEntriesRequest)` | `ScannerServiceTest` — 6 cases (valid / invalid / DOA / DB-quote fallback / detailed-strategy details / both strategy-not-found errors) | **SOLID for outcomes, SHALLOW for cross-cuts** | The `MAX_VALIDATE_SYMBOLS = 30` cap is untested. The `liveQuote.price` is applied via `lastDbQuote.copy(closePrice = liveQuote.price)` — keeping the original date and volume. No test pins this contract; if a future refactor swapped to `liveQuote.date`, breadth lookups (which use `quote.date`) would silently break. The "synthetic only updates price, not indicators" contract on the *validate* path (vs the *checkExits* path which *does* recompute indicators) is implicit and undocumented. |
| `addTrade(AddScannerTradeRequest)` | `ScannerServiceTest` — 6 cases (5 `resolveEntryDate` branches + 1 happy path) | **SOLID** | Resolve-entry-date logic is well pinned. The `STALE_DATE_WARN_DAYS = 7L` warning emission is not asserted (low priority). |
| `rollTrade(Long, RollScannerTradeRequest)` | `ScannerServiceTest` — 1 case | **SHALLOW** | Only exercises an OPTION trade with `closePrice < optionPrice` (negative roll credit). Missing: STOCK rolls (multiplier=1 path), positive roll credits (the typical case), missing-`optionPrice` precondition (`IllegalArgumentException`), `findOpenTrade` guard (already-closed → throws), `expirationDate` parsing failures, the `newDelta` / `newOptionType` / `newOptionPrice` partial-update fallback chain. Six branches, one test. |
| `closeTrade(Long, CloseScannerTradeRequest)` | **NONE** | **NONE** | The most critical gap. `closeTrade` is the rule-encoder method ADR 0001 flags as a candidate to move onto `ScannerTrade`. The PnL formula has two branches (OPTION vs STOCK), uses `optionPrice ?: entryPrice` fallback, includes `rolledCredits`. The status transition stamps `closedAt = LocalDateTime.now()`. None of this is tested. The `findOpenTrade` guard (404 vs 409 distinction the controller depends on) is also untested. |
| `getTrades()` | `ScannerServiceTest` — 3 cases | **SOLID** | Strong. The TODO-tracked `Clock` injection is acknowledged in the source but not covered (acceptable). |
| `getClosedTrades()` | **NONE** | **NONE** | Pass-through to `scannerTradeRepository.findClosed()`. Phase 1.5 candidate to inline. Coverage need: nil — covered transitively by E2E. |
| `getTrade(Long)` | **NONE** | **NONE** | Pass-through to `scannerTradeRepository.findById(id)`. Same. |
| `updateTrade(Long, UpdateScannerTradeRequest)` | `ScannerServiceTest` — 1 case | **SHALLOW** | Happy path only. `findOpenTrade` guard (not-found, already-closed) untested. Per ADR 0001: this method is a 3-line copy-update — `existing.copy(notes = ...)` belongs as `existing.withNotes(...)` on `ScannerTrade`. Phase 1.5 candidate. |
| `deleteTrade(Long)` | `ScannerServiceTest` — 3 cases | **SOLID** | "Delete a closed trade is allowed" branch explicitly pinned — distinguishes from `closeTrade`/`rollTrade`/`updateTrade` which all use `findOpenTrade`. |
| `deleteAllTrades()` | `ScannerServiceTest` — 2 cases | **SOLID** | Happy path + zero-count edge. |
| `getDrawdownStats()` | `ScannerServiceTest` — 2 cases | **SHALLOW** | Live-quote happy + DB-fallback. Missing: peak-equity tracking (`maxOf(settings.peakEquity ?: portfolioValue, currentEquity)` — 4-way nullable interaction); drawdown-percent math when peak > current; the `peakEquity > 0` guard branch; closed-trade realised PnL contribution (every existing test stubs `findClosed()` to empty); the win-rate calculation. |
| `getClosedTradeStats()` | `ScannerServiceTest` — 6 cases (empty / single / by-strategy / option-PnL-pct / all-winners-null-PF / all-losers-zero-PF / breakeven) | **SOLID** | Strong. Clear edge-case ladder. |

**Summary by hat (the roadmap's 5 buckets):**

| Hat | Methods | Coverage state |
|---|---|---|
| **A. Scan-for-entries** | `scan` | SHALLOW — happy + filter only; ranker selection, near-miss building, sector filters, condition failure summary, market-date guard all untested |
| **B. Check-exits-on-trades** | `checkExits` | SOLID — best-tested area in the file (synthetic-quote contract has a 14-case ladder) |
| **C. Validate-entries-against-live-quotes** | `validateEntries` | SOLID for outcomes (valid/invalid/DOA), SHALLOW for the `MAX_VALIDATE_SYMBOLS` cap and the "price-only synthesis" contract |
| **D. Trade-lifecycle CRUD** | `addTrade`, `closeTrade`, `rollTrade`, `updateTrade`, `deleteTrade`, `deleteAllTrades`, `getTrades`, `getClosedTrades`, `getTrade` | UNEVEN — `addTrade` solid, `getTrades`/`deleteTrade`/`deleteAllTrades` solid, `closeTrade` **NONE** (critical gap), `rollTrade` shallow, `getClosedTrades`/`getTrade` pass-through with no direct test |
| **E. Closed-trade analytics** | `getDrawdownStats`, `getClosedTradeStats` | UNEVEN — `getClosedTradeStats` solid (6 cases), `getDrawdownStats` shallow (peak-equity math untested) |

### Per-endpoint coverage inventory — `ScannerController` (`/api/scanner/*`)

| Endpoint | Service method | Existing E2E coverage | Failure mode that would slip through |
|---|---|---|---|
| `POST /scan` | `scan` | **NONE** | JSON shape of `ScanResponse`, 400 on missing strategy, 500 on unexpected exception |
| `POST /check-exits` | `checkExits` | **NONE** | JSON shape of `ExitCheckResponse` (especially `nearExits: List<ExitProximity>` polymorphism) |
| `POST /validate-entries` | `validateEntries` | **NONE** | 400 on missing strategy, JSON shape of `entrySignalDetails: EntrySignalDetails?` polymorphism |
| `GET /trades` | `getTrades` | **NONE** | `tradingDaysHeld: Int?` JSON serialisation (null preservation) |
| `POST /trades` | `addTrade` | **NONE** | 201 status, `LocalDate` JSON parse, 400 on bad enums |
| `PUT /trades/{id}` | `updateTrade` | **NONE** | 404 vs 409 distinction (controller string-matches `"already closed"` to discriminate; brittle branch with zero coverage) |
| `PUT /trades/{id}/close` | `closeTrade` | **NONE** | Same string-matching 404/409 branch. **No test exercises closeTrade rule at all** (compounded with the unit-test gap above) |
| `DELETE /trades/{id}` | `deleteTrade` | **NONE** | 204 status |
| `POST /trades/reset` | `deleteAllTrades` | **NONE** | `{deleted: N}` JSON body shape |
| `GET /trades/closed` | `getClosedTrades` | **NONE** | JSON shape of closed-trade fields (`exitDate`, `closedAt`, `realizedPnl`) |
| `GET /trades/closed/stats` | `getClosedTradeStats` | **NONE** | `null` overall serialisation when no closed trades; `null` profitFactor serialisation |
| `GET /drawdown-stats` | `getDrawdownStats` | **NONE** | `DrawdownStatsResponse` JSON shape |
| `POST /trades/{id}/roll` | `rollTrade` | **NONE** | 400 on missing optionPrice; the `@Transactional` rollback guarantee — if the new `save()` fails after `delete()`, does the delete roll back? **Currently untested.** |
| `POST /option-contracts` | (logic in controller, calls `OptionsDataProvider`) | **NONE** | 30+ lines of delta filtering / expiration sorting / intrinsic-extrinsic split — entirely untested at any layer. |

## Coverage gaps per hat

For each hat: the smallest set of new tests that would let us refactor it with confidence in Phase 2.

### A. Scan-for-entries (`scan`)

Current: 2 tests, both happy-path. The orchestration sweep (symbol resolution → batched concurrent eval → near-miss collection → ranking → failure-summary aggregation) has 5 distinct stages and 0–1 tests per stage.

1. **Ranker selection precedence (unit, 3 cases)** — request `rankerName` overrides strategy default; absent request name uses `entryStrategy.preferredRanker()`; both null falls back to `RandomRanker`.
2. **Near-miss building (unit, 2 cases)** — only `DetailedEntryStrategy` produces near-misses with `conditionsPassed > 0`; the `nearMissLimit` cap is enforced; sort order is `conditionsPassed DESC, rankScore DESC`.
3. **Condition-failure-summary aggregation (unit, 1 case)** — `buildConditionFailureSummary` only fires for `DetailedEntryStrategy`; sums failures by `conditionType`, sorts by `stocksBlocked DESC`, with `totalStocksEvaluated` reflecting the count of *evaluated* stocks (not just matched).
4. **Symbol resolution branches (unit, 3 cases)** — explicit `stockSymbols` (uppercased); `assetTypes` filter via `symbolService.getAll()`; default-all (`stockRepository.findAllSymbols()`). Plus include/exclude sector intersection.
5. **Market-date filter (unit, 1 case)** — when a stock's last quote date does not equal the maximum date in `marketBreadthMap`, the stock is silently skipped from evaluation. Stale-data guard with no current pin.
6. **`POST /api/scanner/scan` E2E (1 case)** — happy-path scan against `BacktestTestDataGenerator` data using `TestEntryStrategy`, asserting `ScanResponse` JSON shape + 400 on unknown strategy.

### B. Check-exits-on-trades (`checkExits`)

Current: 14 tests, the most rigorous area.

1. **`POST /api/scanner/check-exits` E2E (1 case)** — happy-path against a real DB-stored `ScannerTrade` + `BacktestTestDataGenerator` quotes; verifies `ExitCheckResponse` JSON shape including `nearExits: List<ExitProximity>` polymorphism. Acts as the live wire on the synthetic-quote machinery.
2. **Concurrent live-quote fetch error path (unit, 1 case)** — `stockProvider.getLatestQuotes(...)` throws (provider outage); `checkExits` should not propagate, falls through to DB quotes. Currently untested.

### C. Validate-entries-against-live-quotes (`validateEntries`)

Current: 6 cases — strong on outcomes.

1. **`MAX_VALIDATE_SYMBOLS` cap (unit, 1 case)** — request with 50 symbols → 30 results.
2. **Price-only synthesis contract (unit, 1 case)** — assert that the `StockQuote` passed to `entryStrategy.test(...)` has the live `closePrice` but the **original** `date` and `volume`. Pins the divergence from `checkExits`'s synthesis path.
3. **`POST /api/scanner/validate-entries` E2E (1 case)** — happy + 400-on-bad-strategy.

### D. Trade-lifecycle CRUD

Critical gap. `closeTrade` has zero direct unit tests; this is also exactly the method ADR 0001 flags as anemic.

1. **`closeTrade` PnL math (unit, 4 cases)** — STOCK trade PnL `(exitPrice - entryPrice) * quantity`; OPTION trade PnL `(exitPrice - optionPrice) * quantity * multiplier + rolledCredits`; OPTION trade with `optionPrice == null` falling back to `entryPrice`; status transition stamps `closedAt`, `exitPrice`, `exitDate`, `realizedPnl`.
2. **`closeTrade` `findOpenTrade` guard (unit, 2 cases)** — not-found and already-closed → both throw `IllegalArgumentException` with distinct messages so the controller's string-match works. Same 2 cases for `updateTrade` and `rollTrade`.
3. **`rollTrade` STOCK path (unit, 1 case)** — STOCK roll uses `multiplier = 1`.
4. **`rollTrade` missing-`optionPrice` precondition (unit, 1 case)**.
5. **`rollTrade` partial-update fallbacks (unit, 1 case)** — three nullable fields fall back to existing values.
6. **`POST /api/scanner/trades` lifecycle E2E (1 file, 8 cases)** — `add` (201) → `get list` → `update notes` → `close` → `delete`. Plus 404 vs 409 distinction on close.
7. **`POST /api/scanner/trades/{id}/roll` E2E (1 case)** — `@Transactional` rollback: if `save(newTrade)` throws, `delete(oldTrade)` must roll back. **The single most important rollback contract in the file.**

### E. Closed-trade analytics

Current: `getClosedTradeStats` solid (6 cases); `getDrawdownStats` shallow.

1. **Peak-equity tracking (unit, 3 cases)** — `peakEquity` from settings overrides `portfolioValue`; `currentEquity > peakEquity` updates the running max; `currentEquity < peakEquity` produces a positive `currentDrawdownPct`.
2. **Drawdown with closed-trade PnL (unit, 1 case)** — closed trades + open trades both contribute to `currentEquity`. Exists nowhere today.
3. **`GET /api/scanner/drawdown-stats` and `GET /api/scanner/trades/closed/stats` E2E (1 file, 2 cases)** — JSON shape with `null` fields preserved.

## Phase 1 plan — concrete test build-up

### Component → test class → cases

Per ADR 0002 (controller tests use full integration via `AbstractIntegrationTest`, named `*E2ETest`, in `e2e/`) and the convention that orchestration services use mock-based unit tests (`BrokerIntegrationServiceTest` shape).

| Component | Test class | Type | New / Update | Cases |
|---|---|---|---|---|
| `ScannerTrade` (domain) | `ScannerTradeTest` (new, plain JUnit) | unit | **new (PR 0)** | (1) `realisedPnl` STOCK formula `(exitPrice - entryPrice) * quantity`; (2) `realisedPnl` OPTION formula with `optionPrice` set, `multiplier`, and `rolledCredits`; (3) `realisedPnl` OPTION with `optionPrice == null` falls back to `entryPrice`; (4) `withClosed(exitDate, exitPrice, closedAt)` returns a copy with `status = CLOSED`, derived `realisedPnl`, and stamped `closedAt`; (5) `withNotes(notes)` returns a copy with the new notes |
| `ScannerService` — close/update guards | `ScannerServiceTest` | unit (mock) | **update (PR 0)** | (1) `closeTrade` orchestrates load → `withClosed(...)` → save (assert via `argumentCaptor` that the saved trade matches `withClosed` output); (2) `closeTrade` not-found throws `NoSuchElementException`; (3) `closeTrade` already-closed throws `NoSuchElementException` (collapsed to 404, message differentiates); (4) `updateTrade` orchestrates load → `withNotes(...)` → save; (5) `updateTrade` not-found throws `NoSuchElementException`; (6) `updateTrade` already-closed throws `NoSuchElementException`; (7) `rollTrade` STOCK path (`multiplier = 1`); (8) `rollTrade` missing `optionPrice` throws; (9) `rollTrade` partial-update fallbacks for 3 nullable fields; (10) `rollTrade` not-found throws `NoSuchElementException`. PnL math itself moves to `ScannerTradeTest` per ADR 0001 |
| `ScannerService` — scan hat | `ScannerServiceTest` | unit (mock) | **update (PR 1)** | (1) ranker request override; (2) ranker strategy preference; (3) ranker `RandomRanker` fallback; (4) near-miss only via `DetailedEntryStrategy`; (5) `nearMissLimit` cap; (6) condition-failure-summary aggregation; (7) explicit `stockSymbols` uppercased; (8) `assetTypes` filter; (9) sector include/exclude; (10) market-date guard skips stale stocks |
| `ScannerService` — check-exits hat | `ScannerServiceTest` | unit (mock) | **update (PR 1)** | (1) live-quote provider exception falls through to DB quotes; (2) fix existing `checkExits returns empty` test to stub `findOpen` (cosmetic, in PR 0). The 13 existing solid cases stay as-is |
| `ScannerService` — validate-entries hat | `ScannerServiceTest` | unit (mock) | **update (PR 3)** | (1) `validateEntries(symbols.size > MAX_VALIDATE_SYMBOLS)` throws `IllegalArgumentException` (→ 400); (2) price-only synthesis contract: `argumentCaptor` on `entryStrategy.test(...)` quote → live `closePrice` but original `date` and `volume` |
| `ScannerService` — drawdown stats | `ScannerServiceTest` | unit (mock) | **update (PR 3)** | (1) `settings.peakEquity` overrides `portfolioValue`; (2) `currentEquity > peakEquity` raises `peakEquity`; (3) drawdown percent positive when current < peak; (4) closed-trade realised PnL contributes to `currentEquity` (math is correct, just untested) |
| `ScannerController` — scan endpoint | `ScannerScanE2ETest` (new) | E2E | **new (PR 1)** | (1) happy `POST /api/scanner/scan` JSON shape against `BacktestTestDataGenerator` + `TestEntryStrategy`; (2) 400 on unknown entry strategy |
| `ScannerController` — check-exits endpoint | `ScannerCheckExitsE2ETest` (new) | E2E | **new (PR 1)** | (1) `POST /api/scanner/check-exits` happy path with `nearExits: List<ExitProximity>` polymorphism end-to-end |
| `ScannerController` — validate endpoint | `ScannerValidateEntriesE2ETest` (new) | E2E | **new (PR 3)** | (1) happy path; (2) 400 on bad strategy; (3) **400 on `symbols.size > 30`** |
| `ScannerController` — trade lifecycle | `ScannerTradeLifecycleE2ETest` (new) | E2E | **new (PR 2)** | (1) POST 201 with `id != null`; (2) GET list with `tradingDaysHeld` populated; (3) PUT updates notes; (4) PUT close persists CLOSED state; (5) PUT close on already-closed returns **404** (post-collapse, no 409 distinction); (6) PUT close on missing id returns **404**; (7) DELETE 204; (8) GET /trades/closed |
| `ScannerController` — roll + transactional rollback | `ScannerRollE2ETest` (new) | E2E | **new (PR 2)** | (1) happy roll: old gone, new has accumulated credits + `rollCount++`; (2) rollback: when new-save fails, old still present (DB never observes the half-step) |
| `ScannerController` — analytics | `ScannerStatsE2ETest` (new) | E2E | **new (PR 3)** | (1) `GET /drawdown-stats` JSON shape with both open and closed trades; (2) `GET /trades/closed/stats` JSON shape including `null` overall + `null` profitFactor (Jackson serialisation of nullables) |
| `ScannerController` — `POST /option-contracts` | `ScannerOptionContractsE2ETest` (new) | E2E | **new (PR 3)** | (1) happy path: lowest-expiration `delta in 0.70..0.90` contract closest to 0.80 delta. Mock `OptionsDataProvider` via `@MockitoBean` |

**Net new tests:** ~32 new unit-test cases added to existing `ScannerServiceTest` (no new file), plus **6 new E2E test files** with ~17 new cases. Existing 41 unit tests stay as-is.

### Test fixtures to reuse (no new fixtures required)

- **`BacktestTestDataGenerator`** — already populates 50 stocks across 11 sectors with ~60 trading days of stock_quotes + market_breadth_daily + sector_breadth_daily + earnings + order_blocks. Idempotent, shared across `*E2ETest` classes.
- **`TestEntryStrategy` / `TestExitStrategy`** — already registered as `@RegisteredStrategy` named `TestEntry` / `TestExit`. Composite strategies that exercise every condition type.
- **`AbstractIntegrationTest`** — shared Postgres container is already running.
- **`PortfolioControllerE2ETest`** — direct shape model for the new files (jOOQ `DSLContext` autowire for cleanup, `restTemplate` for HTTP, `jsonEntity` helper, `@TestInstance(PER_CLASS)` lifecycle, `@BeforeEach` table cleanup).

### Order of work (what to land first)

1. **PR 0 — `ScannerTrade` rich-domain entity + service migration + 404 collapse + tests.** Lift the PnL formula and state-transition rules to the entity per [ADR 0001](../adr/0001-rich-domain-objects.md):
   - `ScannerTrade.realisedPnl(exitPrice): Double` — consolidates the OPTION/STOCK/`rolledCredits` formula. Single source of truth.
   - `ScannerTrade.withClosed(exitDate, exitPrice, closedAt)` — state-transition copy method.
   - `ScannerTrade.withNotes(notes)` — instance copy method.
   - Migrate `ScannerService.closeTrade(...)` / `updateTrade(...)` to use the entity methods (1–2 lines of orchestration each).
   - **404 collapse**: `findOpenTrade` throws `NoSuchElementException` for both not-found and already-closed; drop `ScannerController` try/catch + string-match (~30 lines removed); rely on `GlobalExceptionHandler`.
   - Add `@Transactional` to `updateTrade` and `deleteTrade`.
   - Fix the cosmetic `findOpen` vs `findAll` test bug.
   - **New entity unit tests** (`ScannerTradeTest`, plain JUnit): `realisedPnl` (4 cases — STOCK / OPTION / OPTION-with-`optionPrice == null` falling back to `entryPrice` / OPTION with `rolledCredits`); `withClosed` invariants; `withNotes` invariants.
   - **Updated `ScannerServiceTest`**: 11 cases for `closeTrade`/`rollTrade`/`updateTrade` guards (now asserting `NoSuchElementException`, no string-match), plus the migrated `closeTrade`/`updateTrade` round-trip via the entity methods.
2. **PR 1 — `scan` + `checkExits` coverage.** ~12 new unit cases (ranker selection, near-miss, condition-failure summary, symbol resolution, market-date guard, live-quote provider exception path) + `ScannerScanE2ETest` + `ScannerCheckExitsE2ETest`. No production changes — pure characterisation (these methods are not on the broken-list).
3. **PR 2 — Trade-lifecycle + roll E2E.** `ScannerTradeLifecycleE2ETest` (8 cases incl. the post-collapse "404 on close-of-already-closed") + `ScannerRollE2ETest` (happy + `@Transactional` rollback contract).
4. **PR 3 — `validateEntries` correction + stats + option-contracts E2E.** Add `require(symbols.size <= MAX_VALIDATE_SYMBOLS)` boundary check to `validateEntries`; updated `ScannerServiceTest` cases assert HTTP 400 on overflow + price-only synthesis contract via `argumentCaptor`. New `ScannerValidateEntriesE2ETest` + `ScannerStatsE2ETest` (incl. `getDrawdownStats` solidification — pure characterisation, math already correct) + `ScannerOptionContractsE2ETest` (mock `OptionsDataProvider` via `@MockitoBean`).

Total: 4 PRs (renumbered 0–3), ~32 unit-test cases + ~17 endpoint cases + entity rich-domain methods + 4 production fixes (404 collapse, MAX_VALIDATE reject, two `@Transactional` annotations, PnL-formula deduplication via entity lift).

## Phase 2 hypothesis (post-Phase-1 evaluation criteria for the split)

Once Phase 1 lands, re-read the test file inventory and ask: **does the test setup naturally cluster into the 3 proposed seams** (`ScannerEvaluator` for read/eval, `ScannerTradeStore` for writes, `ScannerStatsService` for aggregations) **or does it cross-cut**?

Concrete signals:

1. **If the unit-test setup blocks for a single hat consistently mock fewer than 4 of `ScannerService`'s 10 deps, the seam is real.** `getClosedTradeStats` already only touches `scannerTradeRepository`; `getDrawdownStats` touches 4. `scan` touches 7. `checkExits` touches 6. Wide spread → split would shrink test surface area per hat.
2. **If a single E2E test ends up needing all three new services as collaborators, the seam is wrong.**
3. **If `closeTrade` survives Phase 1.5's anemic-rule audit and moves onto `ScannerTrade.withClosed(...)`**, the `ScannerTradeStore` seam shrinks correspondingly. Phase 1.5 may *partially dissolve Phase 2 before Phase 2 starts* — exactly what happened with candidate #5.

**Decision marker:** if after Phase 1.5 the file still reads as 5 hats with shared state, proceed with the split. If the entity-rule lifts have already shrunk the file to ~600 lines and 3 cohesive hats, the split is no longer the highest-leverage move — write that up and close candidate #3 instead.

### Phase 2 readiness check (2026-05-09)

After PRs #14, #15, #16 landed:

| Criterion | Reality | Verdict |
|---|---|---|
| Line count vs ~600 target | 945 lines (PR 0 lifts saved ~30–50 lines) | Not shrunk |
| Hat count | 5 distinct hats remain (`scan` / `checkExits` / `validateEntries` / lifecycle CRUD / stats) | Still 5 |
| Test files cluster into 3 seams | Yes — `Scan` / `CheckExits` / `ValidateEntries` E2Es share synthetic-quote concerns (Evaluator), `Roll` / `TradeLifecycle` E2Es share `findOpenTrade` + lifecycle invariants (TradeStore), `Stats` E2E is pure aggregation | Hypothesis validated |
| Shared state across hats | `createSyntheticQuote` shared by `checkExits` + `validateEntries`; `findOpenTrade` shared by lifecycle methods; 10 constructor deps still used cross-hat | Manageable — becomes explicit interface deps after split |

**Verdict:** Phase 2 is justified — file is still 945 lines, 5 hats, and the test layout cleanly reflects the proposed 3 seams. Deferred (not closed) — low urgency now that coverage is solid; pick up next time anything in `ScannerService.kt` needs changing.

## Phase 1.5 — anemic-rule audit (mostly absorbed into PR 0; remainder deferred)

Resolved via grilling on 2026-05-08:

| Service / method | Resolution |
|---|---|
| `ScannerService.closeTrade(...)` | ✅ Lifted to `ScannerTrade.withClosed(...)` + `realisedPnl(exitPrice)` in PR 0 |
| `ScannerService.updateTrade(...)` | ✅ Lifted to `ScannerTrade.withNotes(notes)` in PR 0 |
| PnL formula 4× duplication | ✅ Consolidated to `ScannerTrade.realisedPnl(exitPrice)` — single source of truth — in PR 0 |

**Still deferred:**

| Service / method | Reason |
|---|---|
| `ScannerService.rollTrade(...)` `rollCredit` calculation | Composes two aggregates (old trade lookup + new trade construction); orchestration, not a single-aggregate rule. The 1-line credit-formula could become `existingTrade.computeRollCredit(closePrice)` on `ScannerTrade` in a follow-up if the test pressure surfaces it. Not load-bearing. |

## Open questions (residual after grilling)

1. **`POST /option-contracts` placement.** Lives entirely inside `ScannerController` (30+ lines of `findBestOptionContract`), calls `OptionsDataProvider` directly. Doesn't fit any of the Phase-2 seams. Likely candidate to extract into a new `OptionContractRecommendationService` or fold into `OptionPriceService`. **Decision deferred to a separate PR** — orthogonal to coverage. Phase 1 still adds an E2E covering the current location; the test will move with the endpoint when the placement is decided.

2. **`scan` chunking + concurrency.** `BATCH_SIZE = 150`, `Dispatchers.Default` per-stock async — currently untested. Unit-testing `runBlocking(Dispatchers.Default)` semantics is awkward. **Defer:** pin via E2E only (PR 1's `ScannerScanE2ETest` exercises the chunking path implicitly). Revisit if a concurrency bug surfaces.

3. **`tradingDaysHeld` N+1 in `getTrades`.** Single-user scale → ~500ms worst case on a manual page reload. Real fix requires restructuring the data fetch (~20 lines). **Defer with `// TODO N+1` comment** in PR 0 so it's visible if it ever surfaces in profiling.

### Critical files for implementation

- `udgaard/src/main/kotlin/com/skrymer/udgaard/scanner/service/ScannerService.kt` (940 lines — read-only target of the audit; no production changes in Phase 1)
- `udgaard/src/test/kotlin/com/skrymer/udgaard/scanner/service/ScannerServiceTest.kt` (~32 new unit cases added to the existing 41)
- `udgaard/src/test/kotlin/com/skrymer/udgaard/e2e/AbstractIntegrationTest.kt` (existing infra, used unchanged)
- `udgaard/src/test/kotlin/com/skrymer/udgaard/e2e/BacktestTestDataGenerator.kt` (existing fixture, reused unchanged)
- `udgaard/src/test/kotlin/com/skrymer/udgaard/e2e/PortfolioControllerE2ETest.kt` (shape reference for the 6 new `Scanner*E2ETest` files)
- `udgaard/src/main/kotlin/com/skrymer/udgaard/scanner/controller/ScannerController.kt` (read-only target — coverage of every endpoint; no production changes in Phase 1)
