# Deepening candidates — 2026-05-07

Architecture review across `udgaard/` and `asgaard/`. Each candidate is a place where a module is **shallow** (interface ≈ implementation) or where complexity has been spread across modules whose seams aren't load-bearing.

Vocabulary: **module** = anything with an interface + impl; **deep** = high leverage at a small interface; **seam** = where an interface lives. **Deletion test**: imagine deleting the module — if complexity vanishes, it was a pass-through; if it reappears across N callers, it was earning its keep.

## Status

| # | Candidate | Smell | Scope | Status |
|---|-----------|-------|-------|--------|
| 1 | `StockPriceChart` chart-marker module | parallel watcher blocks + prop explosion | Small | Planned (see [chart-marker-deepening.md](chart-marker-deepening.md)) |
| 2 | `DynamicStrategyBuilder` `when{}` factory | shallow abstraction (interface ≪ impl) | Medium | Open |
| 3 | `ScannerService` 5-hat module | pass-through + multi-cohesion | Medium | Open |
| 4 | Mirrored Condition modal + table pairs | mirrored modules, parallel maintenance | Medium | Open |
| 5 | Portfolio-service layer (8 services) | pass-through + callers-always-need-N-together | Small (start) → Large | Open |

---

## 1. `StockPriceChart` chart-marker module

**Files:** `asgaard/app/components/StockPriceChart.client.vue` (lines 663–778, 888–900)

The chart accepts three signal *sources* as separate props (`signals`, `conditionSignals`, `exitConditionSignals`). Each has its own `forEach` marker-construction block and its own `watch()`. Adding a fourth source (e.g. proximity-aware near-exit markers, deferred from PR #9) requires touching the chart in 4 places.

**Deletion test:** Replace 3 props with `markers: ChartMarker[]`. Conditional logic moves *up* to the page (which already decides which sources it has), but each block goes from ~25 lines of marker construction to 1 line of mapping. Adding source #4 → 0 chart changes. Chart's interface shrinks from 6 props to 4.

## 2. `DynamicStrategyBuilder` `when{}` factory

**Files:** `udgaard/.../backtesting/service/DynamicStrategyBuilder.kt`, plus all 38 entry + 16 exit condition classes

Adding a new condition today requires editing **three** places: implement the class, add `getMetadata()`, AND add a `when` case in `DynamicStrategyBuilder` mapping `config.type` → constructor invocation. Spring already auto-discovers conditions via `ConditionRegistry` for metadata; the same discovery should drive construction. The factory's public method is thin (`buildEntryCondition(config)`) but the implementation is 356 lines of pure dispatch.

**Deletion test:** Move `parseConfig(params): Condition` to each condition's metadata. The when-dispatch evaporates. `DynamicStrategyBuilder` shrinks to ~30 lines of registry lookup. Adding condition #N+1 becomes a 1-step task.

## 3. `ScannerService` 5-hat module

**Files:** `udgaard/.../scanner/service/ScannerService.kt` (940 lines)

Wears 5 distinct hats: scan-for-entries, check-exits-on-trades, validate-entries-against-live-quotes, trade-lifecycle CRUD (open/close/roll/delete), closed-trade analytics (drawdown, edge, win rate). Tests for any one hat have to load the entire singleton.

**Deletion test:** Three real seams — `ScannerEvaluator` (read-mostly, owns BacktestContext), `ScannerTradeStore` (writes), `ScannerStatsService` (aggregations). The current ScannerService becomes thin orchestration in the controller (or 3 separate controllers — scanner is already URL-prefixed `/scan`, `/trades`, `/drawdown-stats`). Each new module is tightly cohesive; cross-cuts become explicit interface dependencies, not hidden field access.

**Risk:** Subtle behaviour changes — needs solid scanner test coverage first (currently thin).

## 4. Mirrored Condition modal + table pairs

**Files:** `asgaard/app/components/{ConditionConfigModal,ExitConditionConfigModal,ConditionSignalsTable,ExitConditionSignalsTable}.vue`

Each pair is ~85% identical. The recent show-exit-conditions PR copy-pasted both files and tweaked: operator default OR vs AND, required entry-date guard (exit only), inverted badge color (passed=success on entry, passed=error on exit since "passed"="exit fired"), different empty-state copy. Future feature adds (proximity warnings, lookback warnings, named-strategy mode) compound the parallel-maintenance tax.

**Deletion test:** Two real seams — `<ConditionEvaluationModal :mode="'entry' | 'exit'">` + `<ConditionSignalsTable :mode :signals>`. Move all 4 files into `components/condition-eval/` so they're navigable as a unit. Differences become explicit `if (mode === 'exit')` branches in 2 places vs implicit-by-file-naming today.

## 5. Portfolio-service layer

**Files:** `udgaard/.../portfolio/service/{PortfolioService, PositionService, BrokerIntegrationService, PortfolioStatsService, ForexTrackingService, CashTransactionService, OptionPriceService, UnrealizedPnlService}.kt`

8 services, but evidence of false seams: `BrokerIntegrationService` constructor injects 5 of them; `PortfolioController` always uses 3 together for broker-sync; `PositionController` always uses `PositionService + PortfolioStatsService + UnrealizedPnlService` together for position reporting. `PortfolioService` (97 lines) is pure CRUD pass-through to the repo — fails the deletion test (delete it, controller talks to repo, complexity moves but doesn't grow). At least 2-3 of these services are genuinely independent (ForexTrackingService for FX; CashTransactionService for IBKR import — both have distinct callers).

**Deletion test:** Needs per-service usage grep before recommending. Safest first move: delete `PortfolioService` (the thinnest), inline into `PortfolioController` or whichever read-path uses it. Clean 1-pass-through-removed win without breaking the others.
