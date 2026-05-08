# Plan: Portfolio-service layer deepening (candidate #5)

Status: **Open** · Branch: `feature-portfolio-service-deepening` · See [deepening-candidates.md](deepening-candidates.md) for the full list.

## Smell

`udgaard/.../portfolio/service/` holds **8** services totalling **2,238 lines**. Per-service shape:

| Service | Lines | Public methods | Constructor deps | Direct callers (main) |
|---|---:|---:|---|---|
| `PortfolioService` | 97 | 7 (`getAllPortfolios`, `createPortfolio`, `getPortfolio`, `updatePortfolio`, `deletePortfolio`, `updatePortfolioWithBrokerInfo`, `updateLastSyncDate`) | `PortfolioJooqRepository` | `PortfolioController` (5 sites), `BrokerIntegrationService` (4 sites) |
| `PositionService` | 532 | 12 (find/create, addExecution, close, manual create/close, roll-update, metadata, delete, query getters) | `PositionJooqRepository`, `ExecutionJooqRepository`, `PortfolioJooqRepository` | `PositionController`, `BrokerIntegrationService`, `PortfolioStatsService`, `UnrealizedPnlService` |
| `BrokerIntegrationService` | 730 | 3 (`createPortfolioFromBroker`, `syncPortfolio`, `testConnection`) | 9: `BrokerAdapterFactory`, `TradeProcessor`, `PortfolioService`, `PositionService`, `PortfolioStatsService`, `ExecutionJooqRepository`, `ForexTrackingService`, `CashTransactionService`, `MidgaardClient` | `PortfolioController` |
| `PortfolioStatsService` | 300 | 3 (`calculateStats`, `getEquityCurve`, `recalculatePortfolioBalance`) | `PositionService`, `ExecutionJooqRepository`, `PortfolioJooqRepository`, `MidgaardClient`, `CashTransactionService` | `PositionController`, `BrokerIntegrationService` |
| `ForexTrackingService` | 148 | 6 (record/process executions, get lots/disposals) | `ForexLotJooqRepository`, `ForexDisposalJooqRepository` | `BrokerIntegrationService`, `PortfolioController` |
| `CashTransactionService` | 75 | 5 (add, get list, getNetCashFlow, totals) | `CashTransactionJooqRepository` | `BrokerIntegrationService`, `PortfolioStatsService`, `PortfolioController` |
| `OptionPriceService` | 141 | 3 (historical prices, single price, position-aware) | `OptionsDataProvider` | `OptionController` only |
| `UnrealizedPnlService` | 215 | 1 (`calculateUnrealizedPnl`) | `PositionService`, `StockProvider`, `OptionsDataProvider` | `PositionController` only |

**Deletion-test verdict per service:**

| Service | Verdict | Reasoning |
|---|---|---|
| `PortfolioService` | **Pass-through — delete it** | All 7 methods are 1–2 line wrappers over `PortfolioJooqRepository`. Two add `LocalDateTime.now()` mutation (`updatePortfolio`, `updateLastSyncDate`); the rest are pure forwarding. 9 call sites total, all in `PortfolioController` + `BrokerIntegrationService`. No domain logic. **First commit, low-risk.** |
| `PositionService` | **Keep — orchestration** | 12 public methods, 532 lines, with real domain logic: `closePosition` (P&L + base-currency P&L + portfolio balance update — multi-aggregate transaction), `recalculatePositionAggregates` (running-average reset on roll boundaries), `getRollChain` (graph walk), `closeManualPosition` (composes execution + close). Deleting it disperses these into the controller + repo. Genuine seam. |
| `BrokerIntegrationService` | **Keep — orchestration** | The 5-service constructor flagged in the roadmap *is* the deepening: the orchestration sequence (fetch → split → detect rolls → import → balance → cash transactions → forex tracking → balance recalc) is the entire reason this service exists. The 730 lines are nearly all roll-chain / lot-grouping import logic — that's the load-bearing complexity. |
| `PortfolioStatsService` | **Keep — orchestration with derivation** | `calculateStats` weaves positions + executions + cash transactions + portfolio + Midgaard FX into `PositionStats`. `recalculatePortfolioBalance` is a 4-source aggregation (initial + realized PnL + commissions + cash flow). Genuine derivation, not pass-through. |
| `ForexTrackingService` | **Keep — independent** | FIFO forex-lot accounting for AU CGT reporting. Distinct domain (forex disposals, not equities). Two callers (broker import, portfolio controller queries) consume different methods. Deleting it concentrates two bespoke FIFO algorithms into callers. |
| `CashTransactionService` | **Keep — independent** | Idempotent cash-transaction ingestion (deduplication via `brokerTransactionId`) + small aggregations. Callers are heterogeneous (broker import for write, stats service for sums, controller for read) — false-seam test fails: deletion would move the dedup logic into `BrokerIntegrationService` *and* duplicate aggregation in two more places. |
| `OptionPriceService` | **Keep — independent** | Single caller (`OptionController`), but the loop over weekdays + Greeks-aware `getOptionDataForPosition` is real per-position derivation and isn't natural inside the controller. Mid-priority candidate to revisit later — see Phase 2 hypothesis. |
| `UnrealizedPnlService` | **Keep — single-purpose deep** | One method, but 215 lines of roll-pair detection + live-quote batching + per-position aggregation. The "1 method ≪ 215 line implementation" shape is the textbook *deep* module — high leverage at a small interface. Don't touch. |

**Net:** **1 of 8 services fails the deletion test** — `PortfolioService`. The other 7 are either genuine orchestration (where the *current* dependency mesh is the load-bearing complexity, not a seam to break) or genuinely independent. The roadmap's instinct ("at least 2-3 are genuinely independent — `ForexTrackingService`, `CashTransactionService`") holds. The "callers always need 3 together" observation is real but doesn't imply re-fragmentation: it implies the controllers are honest *coordinators*, which is the right shape.

## Target shape

After Phase 1: **7 services, ~2,141 lines**. `PortfolioController` and `BrokerIntegrationService` talk directly to `PortfolioJooqRepository`.

Per [ADR 0001 — Rich domain objects](../adr/0001-rich-domain-objects.md), the three small invariants currently encoded in `PortfolioService` move onto the `Portfolio` data class itself, not into the controller and not into `BrokerIntegrationService`:

| Old location | New location | Rule encoded |
|---|---|---|
| `PortfolioService.createPortfolio(name, initialBalance, currency, userId)` | `Portfolio.create(name, initialBalance, currency, userId)` (companion-object factory) | new portfolio's `currentBalance == initialBalance`; `createdDate == lastUpdated == now` |
| `PortfolioService.updatePortfolio(id, balance)` | `portfolio.withBalanceUpdated(newBalance)` (instance method on `Portfolio`) | balance edit bumps `lastUpdated` |
| `PortfolioService.updateLastSyncDate(id, ts)` | `portfolio.withSyncCompleted(ts)` (instance method) | sync stamps both `lastSyncDate` and `lastUpdated` |

Pass-through methods (`getAllPortfolios`, `getPortfolio`, `deletePortfolio`, `updatePortfolioWithBrokerInfo`) collapse to direct repo calls at the 4 call sites (controller + broker import).

The repository remains the seam. This is consistent with the rest of the layer: `PositionService` already injects `PositionJooqRepository`, `ExecutionJooqRepository`, *and* `PortfolioJooqRepository` directly — the repo-as-seam pattern is the established convention here, and `PortfolioService`'s wrapper is the outlier.

## Phase 1 plan — `PortfolioService` deletion

### Step-by-step

1. **Add factory + copy methods to `Portfolio`** (per [ADR 0001](../adr/0001-rich-domain-objects.md)):
   - `companion object { fun create(name: String, initialBalance: BigDecimal, currency: String, userId: String?): Portfolio }` — returns a `Portfolio` with `currentBalance == initialBalance`, `createdDate == lastUpdated == now()`, `id == null` (assigned by the repo).
   - `fun withBalanceUpdated(newBalance: BigDecimal): Portfolio` — `copy(currentBalance = newBalance, lastUpdated = now())`.
   - `fun withSyncCompleted(syncedAt: LocalDateTime): Portfolio` — `copy(lastSyncDate = syncedAt, lastUpdated = syncedAt)`.
   - Direct unit tests for each (no Spring context).

2. **Migrate `PortfolioController` callers (5 sites):**
   - `getAllPortfolios(userId)` → `if (userId != null) portfolioRepository.findByUserId(userId) else portfolioRepository.findAll()`. Inline (4-line conditional) or a private helper.
   - `createPortfolio(...)` → `portfolioRepository.save(Portfolio.create(name, initialBalance, currency, userId))`. One line. The factory enforces the invariant.
   - `getPortfolio(id)` → `portfolioRepository.findById(id)`.
   - `updatePortfolio(id, balance)` → `portfolioRepository.findById(id)?.withBalanceUpdated(balance)?.let(portfolioRepository::save)`. The `withBalanceUpdated` call enforces the timestamp bump.
   - `deletePortfolio(id)` → `portfolioRepository.delete(id)`. **Re-add `@Transactional` on the controller method** (CASCADE delete must stay transactional). Verify schema CASCADE in `udgaard/src/main/resources/db/migration/` covers positions+executions before relying on it.
   - sync handler `getPortfolio(id)` → `portfolioRepository.findById(id)`.

3. **Migrate `BrokerIntegrationService` callers (4 sites):**
   - `createPortfolio(...)` (during broker-import) → `portfolioRepository.save(Portfolio.create(name, initialBalance, currency, userId))`. The follow-up `portfolio.copy(broker = ..., brokerAccountId = ..., ...)` block remains as `portfolioRepository.save(savedPortfolio.copy(broker = ..., ...))` — broker-info attachment is orchestration, not a domain rule, so it stays as a `copy()` at the orchestration site.
   - `updatePortfolioWithBrokerInfo(updatedPortfolio)` → `portfolioRepository.save(updatedPortfolio)`.
   - `getPortfolio(portfolioId)` → `portfolioRepository.findById(portfolioId)`.
   - `updateLastSyncDate(portfolioId, now)` → `portfolioRepository.findById(portfolioId)?.withSyncCompleted(now)?.let(portfolioRepository::save)`. The `withSyncCompleted` call enforces both stamps.

4. **Add `PortfolioJooqRepository` to `BrokerIntegrationService`'s constructor; drop `PortfolioService`.** Constructor goes from 9 deps to 9 deps (swap, not add). Net field count unchanged. Drop the import.

5. **Delete `PortfolioService.kt`.**

6. **Update `BrokerIntegrationServiceTest`:**
   - `private val portfolioService: PortfolioService = mock()` → `private val portfolioRepository: PortfolioJooqRepository = mock()`.
   - Constructor positional arg swap.
   - `whenever(portfolioService.getPortfolio(...))` → `whenever(portfolioRepository.findById(...))` (4 sites).
   - Verify no `portfolioService.updateLastSyncDate` or `portfolioService.updatePortfolioWithBrokerInfo` `verify` calls (grep first).

7. **Run tests + manual smoke** (see test coverage table below).

### Component → test coverage

Per [ADR 0002 — Controller tests use full integration](../adr/0002-controller-tests-use-full-integration.md), the new controller test extends `AbstractIntegrationTest` (`*ControllerIT` naming, real Postgres, real HTTP). The CASCADE check folds into the controller-IT instead of being a separate file.

| Component | Test class | Test cases |
|---|---|---|
| `Portfolio` (domain class) | `PortfolioTest` (**new**, plain JUnit, no Spring) | (1) `Portfolio.create(...)` sets `currentBalance == initialBalance`, `createdDate ≈ now`, `lastUpdated ≈ now`, `id == null`, `lastSyncDate == null`, `broker == MANUAL`; (2) `withBalanceUpdated(newBalance)` returns a copy with the new balance and bumped `lastUpdated`, leaves `createdDate` untouched; (3) `withSyncCompleted(syncedAt)` sets both `lastSyncDate` and `lastUpdated` to the supplied timestamp, leaves `createdDate` untouched. |
| `PortfolioController` | `PortfolioControllerE2ETest` (**new — first controller-level E2E**, extends `AbstractIntegrationTest`, lives in `e2e/`) | (1) `GET /api/portfolio` returns the persisted list (assert array length + name); (2) `GET /api/portfolio?userId=foo` filters by user; (3) `POST /api/portfolio` returns 201 with body whose `currentBalance == initialBalance` and `id != null` (verifies `Portfolio.create()` invariants traverse JSON serialisation); (4) `POST /api/portfolio` returns 400 when `initialBalance` ≤ 0 (`@Valid` triggers); (5) `GET /api/portfolio/{id}` returns 404 when missing; (6) `PUT /api/portfolio/{id}` updates balance and bumps `lastUpdated`; (7) `DELETE /api/portfolio/{id}` returns 204 *and* CASCADE-removes child positions+executions+forex-lots+cash-transactions (covers the `@Transactional` migration); (8) `POST /api/portfolio/{id}/sync` with non-existent ID returns 404 with `BrokerErrorResponse` body. |
| `BrokerIntegrationService` | `BrokerIntegrationServiceTest` (**update**, mock-based stays correct here — orchestration logic) | Existing 4 `whenever(portfolioService.getPortfolio…)` calls switched to `portfolioRepository.findById`; existing test names + bodies unchanged. **Add 1 new case:** `createPortfolioFromBroker` saves the portfolio twice — once for ID assignment, once after `copy(broker = …, baseCurrency = …)` — `verify(portfolioRepository, times(2)).save(any())`. **Add 1 new case for sync:** after successful sync, `verify(portfolioRepository).save(argThat { lastSyncDate != null && lastUpdated == lastSyncDate })`. |
| Frontend smoke | manual only (per `feedback_no_ui_tests_for_now`) | Open `pages/portfolio.vue`: list portfolios, create new, edit balance, delete, run broker import, run broker sync. Each must exercise its respective controller endpoint; no JSON shape changed; expect zero visible difference. |

### Backtest / scanner impact

**Verify zero impact** with three greps:

- `grep -r "PortfolioService" udgaard/src/main/kotlin/com/skrymer/udgaard/backtesting` → expect no matches
- `grep -r "PortfolioService" udgaard/src/main/kotlin/com/skrymer/udgaard/scanner` → expect no matches
- `grep -r "PortfolioJooqRepository" udgaard/src/main/kotlin/com/skrymer/udgaard/backtesting` → expect no matches (backtest is portfolio-agnostic; uses synthetic ledgers)

The backtest engine has its own `BacktestContext` ledger and never sees `Portfolio`. Phase 1 is structurally invisible to backtest + scanner.

### PR shape

**Single commit / PR.** Diff:
- 1 file deleted (`PortfolioService.kt`)
- 1 entity expanded (`Portfolio.kt` — companion `create()` + `withBalanceUpdated()` + `withSyncCompleted()`)
- 1 controller updated (`PortfolioController.kt` — 5 call sites moved to repo + factory/copy methods)
- 1 service updated (`BrokerIntegrationService.kt` — 4 call sites + constructor swap)
- 1 test class updated (`BrokerIntegrationServiceTest.kt` — mock swap + 2 new cases)
- 2 new test classes: `PortfolioTest` (plain JUnit, ~3 cases), `PortfolioControllerE2ETest` (extends `AbstractIntegrationTest`, lives in `e2e/`; ~8 cases — first controller-level E2E in this codebase per [ADR 0002](../adr/0002-controller-tests-use-full-integration.md))

Total: ~250 lines changed; net coverage *increases* (zero existing controller-test infrastructure → 8 new endpoint-level integration cases).

### Verification

1. `cd udgaard && ./gradlew test` — green, including new `PortfolioControllerTest` and updated `BrokerIntegrationServiceTest`.
2. `/pre-commit` — ktlint + detekt + compiler clean.
3. **Manual smoke:**
   - List portfolios in the UI
   - Create a manual portfolio → confirm row appears with correct balance
   - Edit a portfolio's `currentBalance` → confirm `lastUpdated` advances
   - Delete a test portfolio that has positions → confirm positions disappear too (CASCADE)
   - Run a broker import + sync against the IBKR Flex test query → confirm `lastSyncDate` advances and no duplicate positions
4. **Pre/post comparison:** `curl http://localhost:9080/udgaard/api/portfolio` JSON shape unchanged.

## Phase 1.5 — anemic-rule audit (record only, no fixes this PR)

[ADR 0001 — Rich domain objects](../adr/0001-rich-domain-objects.md) (Fowler-anchored against the [Anemic Domain Model](https://martinfowler.com/bliki/AnemicDomainModel.html) antipattern) likely applies elsewhere. After Phase 1 lands, walk through `udgaard/src/main/kotlin/com/skrymer/udgaard/**/service/*.kt` and flag any service method matching the anemic shape:

- 1–3 lines, single repo call, no external-system orchestration
- Mutates a `data class` field with a derived value (`now()`, `prev + delta`, etc.)
- The rule it encodes belongs naturally on the entity ("a Position is closed by ...", "an Execution's fxRate is captured at ...")

**Candidates to investigate** (initial scan — confirm or reject when audited):

| Service / method | Likely entity rule | Rich-domain shape |
|---|---|---|
| `PositionService.closePosition(...)` | "a Position transitions to CLOSED with realized P&L" | `position.withClosed(closeDate, closePrice, fxRate)` returning a new `Position` plus a derived `realizedPnl` |
| `PositionService.recalculatePositionAggregates(...)` | "a Position's running-average resets on roll boundaries" | `position.withAggregatesRecalculated(executions)` |
| `ScannerService.closeTrade(...)` | "a ScannerTrade transitions to CLOSED with P&L" | `scannerTrade.withClosed(closeDate, closePrice)` |
| `TradeProcessor.splitPartialCloses(...)` / `detectOptionRolls(...)` | derivation rules over raw `Execution` lists | possibly `Execution.Companion.splitPartialCloses(...)` or stays as orchestration if it composes broker-format → domain |
| `CashTransactionService.addCashTransaction(...)` | dedup-by-`brokerTransactionId` + portfolio FK invariant | likely stays in service (dedup is repo-aware, not a pure entity invariant) |

**Process for each candidate:** apply the deletion test from `deepening-candidates.md`. If the service method survives without the entity (i.e., the rule is a 1-liner over a single aggregate) → move it to the entity. If it composes multiple aggregates or external systems → leave it in the service.

**Output of Phase 1.5:** a list of follow-up commits, each scoped to one entity. Not a single sweep — each gets its own deletion-test pass and its own PR.

## Phase 2 hypothesis

**If Phase 1 lands cleanly, the next candidates to consider — in priority order, but each requires its own deletion test before committing:**

1. **`OptionPriceService`** — single caller (`OptionController`), 141 lines. The weekday-loop + Greeks logic might collapse into a thin extension on `OptionsDataProvider` plus a 5-line controller method. Worth a focused deletion-test pass; this is the smallest "second pass-through suspect" remaining.

2. **`CashTransactionService.getNetCashFlow` / `getTotalDeposits` / `getTotalWithdrawals`** — these three are pass-through to `repository.getNetCashFlow` / `repository.sumByType`. The `addCashTransaction` method *isn't* — it has dedup logic. A targeted move: keep the service but inline the three repo-passthroughs at their two callers (`PortfolioController` cash-summary endpoint + `PortfolioStatsService.calculateStats`). Smaller win than #1.

3. **`PortfolioController` is now a 4-dependency God-controller** (`BrokerIntegrationService`, `ForexTrackingService`, `CashTransactionService`, `PortfolioJooqRepository`). After Phase 1, splitting it into `PortfolioController` + `ForexController` + `CashTransactionController` (URL-prefix split, not service-layer split) might be the more natural follow-up than further service consolidation. Different layer; defer.

**Hard line:** The "callers always need 3 together" observation in the original roadmap entry — `PositionController` injects `PositionService + PortfolioStatsService + UnrealizedPnlService` together; `PortfolioController` (broker-sync paths) injects `PortfolioService + BrokerIntegrationService + …` — does *not* warrant merging those services. They are honest orchestration seams: each service is independently testable, each has distinct test setup costs. Merging them would *grow* test scope per-feature, not shrink it. The shape is correct.

**One-paragraph next-move (re-evaluate after Phase 1):** if `PortfolioService` deletion lands clean and the 4-dep `PortfolioController` remains readable, drill into `OptionPriceService` next. If `PortfolioController` starts feeling cramped post-deletion, prefer the URL-prefix controller split (forex/cash carved into their own controllers) over further service-layer changes — service consolidation is not the lever we want to pull twice in a row in the same module.

## Open questions

1. ~~**`@Transactional` on `deletePortfolio`**~~ — **Resolved (grilling, 2026-05-08).** Schema in `V1__initial_schema.sql` has `ON DELETE CASCADE` on `positions.portfolio_id` and `executions.position_id` (transitively). `V11__Add_fx_tracking.sql` adds CASCADE on `forex_lots`/`forex_disposals`. `V13__Add_cash_transactions.sql` adds CASCADE on `cash_transactions`. The DB enforces atomicity for `DELETE FROM portfolios`. The `@Transactional` annotation on the controller method is belt-and-braces, not load-bearing.

2. ~~**`BrokerIntegrationService.createPortfolioFromBroker` shape**~~ — **Dissolved by ADR 0001.** With `Portfolio.create(...)` as the entity factory, the broker-create path becomes naturally 2 lines: `val saved = portfolioRepository.save(Portfolio.create(name, initialBalance, currency, userId))` then `portfolioRepository.save(saved.copy(broker = ..., brokerAccountId = ..., baseCurrency = ..., initialFxRate = ...))`. Broker-info attachment is orchestration (it composes `BrokerAccountInfo` from the integration layer with the freshly-saved portfolio's ID), not a domain rule, so it stays as a `.copy()` at the orchestration site. No private helper needed.

3. **Terminology drift (deferred to its own pass — not Phase 1 scope):** `PortfolioController.kt` comment says *"Position/Trade management is in PositionController"* — the codebase mostly uses **Position** + **Execution** as the canonical pair (per `PositionService`, `Execution` model, `PositionWithExecutions`), but **Trade** also leaks in (`tradesAdded`, `tradesUpdated` on `PortfolioSyncResult`; `BrokerIntegrationService.importTrades`; `TradeProcessor`, `TradeLot`). After candidate #2 (DynamicStrategyBuilder) settled "EntryCondition / ExitCondition" as the canonical pair, this is the next vocabulary clean-up to consider — but it's broader than Phase 1 and should not be snuck into a deletion commit.

### Critical files for implementation

- `udgaard/src/main/kotlin/com/skrymer/udgaard/portfolio/service/PortfolioService.kt` (deleted)
- `udgaard/src/main/kotlin/com/skrymer/udgaard/portfolio/controller/PortfolioController.kt` (5 call sites migrated to repo)
- `udgaard/src/main/kotlin/com/skrymer/udgaard/portfolio/service/BrokerIntegrationService.kt` (constructor swap + 4 call sites migrated)
- `udgaard/src/test/kotlin/com/skrymer/udgaard/portfolio/service/BrokerIntegrationServiceTest.kt` (mock swap)
- `udgaard/src/main/kotlin/com/skrymer/udgaard/portfolio/repository/PortfolioJooqRepository.kt` (becomes the seam)
