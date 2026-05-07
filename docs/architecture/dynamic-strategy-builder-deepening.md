# Plan: DynamicStrategyBuilder factory deepening (candidate #2)

Status: **Planned** · Branch: `feature-architectur-tidyup` · See [deepening-candidates.md](deepening-candidates.md) for the full list.

## Why

Adding a new entry or exit condition today is a **3-place** edit:

1. Implement the condition class (`@Component`, constructor params, `evaluate` / `shouldExit`, `description`, `evaluateWithDetails`)
2. Implement `getMetadata()` on that class — already required by the interface; consumed by `ConditionRegistry.getEntryConditionMetadata()` for `GET /api/backtest/conditions`
3. Add a `when` branch to `DynamicStrategyBuilder` mapping `config.type.lowercase()` → constructor invocation, hand-copying every parameter default

Step 3 is pure dispatch. `DynamicStrategyBuilder.kt` is **355 lines** — `buildEntryCondition` ~150 lines (38 conditions) + `buildExitCondition` ~80 lines (16 conditions). 100% `when (type) { … }` glue.

Concrete pain:

- **Defaults are duplicated** across constructor, `getMetadata().parameters[].defaultValue`, and `?: default` in the `when` arm — three copies, drift waiting to happen.
- **Auto-discovery already exists for metadata, not for construction.** `ConditionRegistry(entryConditions: List<EntryCondition>, exitConditions: List<ExitCondition>)` proves Spring discovers all condition implementations. Metadata uses that discovery; the builder duplicates it as a hand-maintained `when` table.
- **The class-name → wire-type binding is implicit.** `getMetadata().type = "atrExpanding"` and the `when` case `"atrexpanding" -> ATRExpandingCondition(…)` are in different files with no compile-time link.
- **Untestable per-condition.** "What params does this condition accept and what defaults?" lives in three files; no single test asserts the parser for one condition.

The factory **fails the deletion test**: deleting it pushes the dispatch into the registry as a one-line lookup, not a 230-line switch.

## What deepens

Each condition becomes responsible for **parsing its own config**. The registry routes a `ConditionConfig` to the right condition by `type.lowercase()` and asks it to build itself.

**Result:**

- `DynamicStrategyBuilder.kt`: 355 lines → ~50 lines. Two `when` tables disappear; only the entry-AND / exit-OR operator default and the AND-on-exit warning survive (load-bearing — different defaults are real domain semantics).
- Adding condition #N+1: implement the class, override `parseConfig`, done. **No third file to touch.** Wire-type binding is type-checked at registry boot (duplicate `type` strings throw).
- Defaults collapse to **one** source of truth: the class constructor. `getMetadata()` reads `this.field` instead of duplicating the literal. Drift becomes structurally impossible.
- Per-condition parsing is unit-testable in isolation: `ATRExpandingCondition().parseConfig(mapOf("minPercentile" to 25.0))` returns a configured instance.

## Locked design

### Interface additions

`EntryCondition.kt` — append:

```kotlin
/**
 * Build a new instance of this condition with the given parameters.
 * Unknown keys are ignored. Missing keys fall back to this instance's defaults
 * (the singleton bean's constructor defaults — single source of truth).
 *
 * Wire-type binding: the registry routes by getMetadata().type; this method does not see the type.
 */
fun parseConfig(parameters: Map<String, Any>): EntryCondition
```

`ExitCondition.kt` — same signature, returns `ExitCondition`.

Both interfaces stay otherwise unchanged.

### Per-condition implementations

The Spring-managed singleton holds default constructor values; `parseConfig` falls back to `this.field`:

```kotlin
override fun parseConfig(parameters: Map<String, Any>): EntryCondition =
  ATRExpandingCondition(
    minPercentile = parameters.numberOr("minPercentile", this.minPercentile),
    maxPercentile = parameters.numberOr("maxPercentile", this.maxPercentile),
    lookbackPeriod = parameters.intOr("lookbackPeriod", this.lookbackPeriod),
  )
```

Each condition's `getMetadata()` also switches its literal defaults to `this.field`:

```kotlin
ParameterMetadata(name = "minPercentile", defaultValue = this.minPercentile, …)
```

The metadata JSON shape doesn't change — frontend stays untouched.

### Helper extensions

New file `udgaard/.../backtesting/service/ConditionConfigParsing.kt`:

```kotlin
internal fun Map<String, Any>.numberOr(key: String, default: Double): Double =
  (this[key] as? Number)?.toDouble() ?: default

internal fun Map<String, Any>.intOr(key: String, default: Int): Int =
  (this[key] as? Number)?.toInt() ?: default

internal fun Map<String, Any>.stringOr(key: String, default: String): String =
  (this[key] as? String) ?: default
```

`internal` keeps them scoped to the backtesting module.

### Registry deepens

```kotlin
@Service
class ConditionRegistry(
  entryConditions: List<EntryCondition>,
  exitConditions: List<ExitCondition>,
) {
  private val entryByType: Map<String, EntryCondition> =
    entryConditions.associateByTypeOrThrow { it.getMetadata().type.lowercase() }
  private val exitByType: Map<String, ExitCondition> =
    exitConditions.associateByTypeOrThrow { it.getMetadata().type.lowercase() }

  fun buildEntryCondition(config: ConditionConfig): EntryCondition =
    (entryByType[config.type.lowercase()]
      ?: throw IllegalArgumentException("Unknown entry condition type: ${config.type}"))
      .parseConfig(config.parameters)

  fun buildExitCondition(config: ConditionConfig): ExitCondition =
    (exitByType[config.type.lowercase()]
      ?: throw IllegalArgumentException("Unknown exit condition type: ${config.type}"))
      .parseConfig(config.parameters)

  fun getEntryConditionMetadata(): List<ConditionMetadata> = entryByType.values.map { it.getMetadata() }
  fun getExitConditionMetadata(): List<ConditionMetadata> = exitByType.values.map { it.getMetadata() }
}
```

`associateByTypeOrThrow` is a tiny private helper that throws `IllegalStateException` if two conditions register the same type — catches conflicts at boot, not at runtime.

### Factory shrinks

`DynamicStrategyBuilder` keeps only strategy-level concerns:

```kotlin
@Service
class DynamicStrategyBuilder(
  private val strategyRegistry: StrategyRegistry,
  private val conditionRegistry: ConditionRegistry,
) {
  fun buildEntryStrategy(config: StrategyConfig): EntryStrategy? = when (config) {
    is PredefinedStrategyConfig -> strategyRegistry.createEntryStrategy(config.name)
    is CustomStrategyConfig -> CompositeEntryStrategy(
      conditions = config.conditions.map { conditionRegistry.buildEntryCondition(it) },
      operator = parseOperator(config.operator, default = LogicalOperator.AND),
      description = config.description,
    )
  }

  fun buildExitStrategy(config: StrategyConfig): ExitStrategy? = …  // mirror, default OR + AND-warn
}
```

The two giant `when` tables disappear. Per-build `logger.info` lines are dropped — the `IllegalArgumentException` for unknown types still surfaces with the offending type in its message.

### Special-parsing edge cases that need explicit handling

These are conditions where the current builder does *more* than a single `(value as? Number)?.toX()`. Each must be preserved verbatim in its `parseConfig`:

| Condition | Special behaviour |
|---|---|
| `MarketBreadthEmaAlignmentCondition` | `emaPeriods` is a `String` like `"5,10,20"`, comma-split to `List<Int>`. Default `listOf(5, 10, 20)`. |
| `SectorBreadthIncreasingCondition` | `sectorSymbol` is a `String` (default `"XLK"`). |

### Dropped: `"orderblock"` deprecated alias

Grep across `udgaard/`, `asgaard/`, `strategy_exploration/`, `pinescripts/` shows the only reference is the builder's own `when` arm — no live callers. The alias is dropped; only `"bearishorderblock"` registers (matches `getMetadata().type` on `BearishOrderBlockExit`).

### Caller migration

- `StrategySignalService.kt:168` — `dynamicStrategyBuilder.buildEntryCondition(it)` → `conditionRegistry.buildEntryCondition(it)`
- `StrategySignalService.kt:229` — `dynamicStrategyBuilder.buildExitCondition(it)` → `conditionRegistry.buildExitCondition(it)`
- `StrategySignalService` constructor adds `ConditionRegistry`. `DynamicStrategyBuilder` stays injected for full-strategy paths.

Test mocks (`StrategySignalServiceTest`, `ScannerServiceTest`) update their per-condition mock setups to mock `ConditionRegistry` instead of `DynamicStrategyBuilder` for the relocated calls.

### Behavioural-parity verification

A transient `LegacyDynamicStrategyBuilder` test fixture lives in `udgaard/src/test/.../backtesting/service/legacy/`. It's a copy-paste of the current `when`-based code. Header comment: `// DELETE WITH PR #XX — parity fixture for ConditionRegistry deepening`.

`ConditionRegistryParityTest` (parameterised over every registered condition):

1. Build the condition via `LegacyDynamicStrategyBuilder` with default `ConditionConfig(type, emptyMap())`
2. Build the condition via `ConditionRegistry`
3. Assert `description()` and serialized `getMetadata()` are equal
4. For conditions with parameters, also build with one non-default value per param; assert equality

The fixture and the parity test get deleted in the same commit that flips `DynamicStrategyBuilder` to use the registry, after the parity test goes green. A pre-commit `grep` check verifies no `LegacyDynamicStrategyBuilder` references remain post-deletion.

## Critical files

**Interfaces:**
- `udgaard/.../strategy/condition/entry/EntryCondition.kt` — add `parseConfig` (+ KDoc)
- `udgaard/.../strategy/condition/exit/ExitCondition.kt` — add `parseConfig` (+ KDoc)

**Registry (deepens):**
- `udgaard/.../backtesting/service/ConditionRegistry.kt` — add `entryByType`/`exitByType` maps + `buildEntryCondition`/`buildExitCondition`; assert no duplicate `type` strings at boot

**Factory (shrinks):**
- `udgaard/.../backtesting/service/DynamicStrategyBuilder.kt` — drop both `when` tables; inject `ConditionRegistry`; keep `buildEntryStrategy`/`buildExitStrategy` predefined-vs-custom dispatch + composite assembly + AND-on-exit warning + drop per-build info logs

**Helpers (new):**
- `udgaard/.../backtesting/service/ConditionConfigParsing.kt` — `numberOr`/`intOr`/`stringOr` extensions, `internal` visibility

**Conditions (~54 files, one small additive override each):**
- `udgaard/.../strategy/condition/entry/*.kt` — 38 entry condition files; each gets a ~3–6 line `parseConfig` override + switches `getMetadata().defaultValue` literals to `this.field`
- `udgaard/.../strategy/condition/exit/*.kt` — 15 exit condition files; same pattern

**Callers:**
- `udgaard/.../backtesting/service/StrategySignalService.kt:168,229` — switch per-condition calls to `conditionRegistry`

**Test fixture (transient, deleted same PR):**
- `udgaard/src/test/.../backtesting/service/legacy/LegacyDynamicStrategyBuilder.kt` — verbatim copy of pre-refactor builder, used only by the parity test

## Tests

| Component | Test class | Cases |
|---|---|---|
| `ConditionRegistry` | `ConditionRegistryTest` (new) | (1) `buildEntryCondition` returns same class as the legacy `when` for every registered entry type; (2) `buildExitCondition` same for exit types; (3) duplicate-`type` detection throws at construction (force two beans with same type into a test list); (4) unknown type throws `IllegalArgumentException` with offending type in message |
| Per-condition `parseConfig` | `ConditionParseConfigTest` (new, parameterised over Spring-discovered conditions) | (1) `parseConfig(emptyMap()).description() == defaultInstance.description()` (defaults round-trip); (2) one explicit non-default value per parameterised condition flows through to `description()` |
| Behavioural parity | `ConditionRegistryParityTest` (new, parameterised; deleted with the legacy fixture) | Build every condition via legacy and via registry; assert `description()` + serialized `getMetadata()` equality, both with default and with one non-default param |
| Special-parsing edge cases | `MarketBreadthEmaAlignmentConditionTest` (new or extend) | `parseConfig(mapOf("emaPeriods" to "3,7,15"))` → instance reflects those periods; `parseConfig(emptyMap())` → defaults `[5, 10, 20]` |
| Special-parsing edge cases | `SectorBreadthIncreasingConditionTest` (new or extend) | `parseConfig(mapOf("sectorSymbol" to "XLF"))` → `XLF`; missing key → `XLK` |
| Existing test sites | `StrategySignalServiceTest`, `ScannerServiceTest` | Update `Mockito.mock(DynamicStrategyBuilder)` setups: switch the per-condition mocks to `mock(ConditionRegistry)` where the call site moved; keep strategy-level builder mocks unchanged |

`ConditionParseConfigTest` discovers conditions via the Spring context (constructor-instantiated list) so adding condition #N+1 auto-extends the parameterised set — no manual test maintenance per condition.

## PR shape

**Single migration PR** on `feature-architectur-tidyup`. The interface change and the 54 implementations are coupled — having one without the other is strictly worse than either pre-refactor or post-refactor. The per-condition diff is mechanical (3-line overrides), so reviewer fatigue is mitigated by the per-file repetition. The legacy fixture and parity tests live entirely inside the PR.

## Out of scope

- **Merging entry/exit hierarchies.** Different shapes (`evaluate(stock, quote, context)` vs `shouldExit(stock, entryQuote, quote, context)` + `proximity` + `exitReason`). Unifying them would obscure real domain semantics.
- **Aliasing infrastructure.** Pre-flight grep settled it — drop `"orderblock"`, no aliases needed.
- **Tightening `Map<String, Any>` to a typed param shape.** Richer refactor (DTO per condition with Jackson polymorphism) for marginal extra type safety. Defer until parsing bugs justify it.
- **Reworking `StrategyRegistry`** (predefined-strategy registry). Different module; works fine.
- **Frontend changes.** `GET /api/backtest/conditions` response is unchanged.

## Verification

1. **Backend tests pass:** `cd udgaard && ./gradlew test` — full suite, including the three new test classes. The parity test is the load-bearing one.
2. **Pre-commit clean:** `/pre-commit` (ktlint, detekt, compiler warnings, frontend skipped — no UI changes)
3. **API contract smoke:** `curl http://localhost:9080/udgaard/api/backtest/conditions` returns the same JSON shape, same condition counts, same parameter defaults as before. Diff against a captured pre-refactor response.
4. **Backtest reproducibility:** rerun an existing custom-DSL backtest fixture (e.g. the VCP custom-DSL config used in PRD) — same trades, equity curve, summary stats within numerical noise.
5. **Manual smoke** — pick one entry + one exit condition that uses non-default params, fire `POST /api/stocks/AAPL/condition-signals` and `POST /api/stocks/AAPL/exit-condition-signals` from the stock-data page. Same markers as before.
6. **Add-condition workflow check** (throwaway commit, don't merge): add a no-op `DummyEntryCondition` with `@Component`, `getMetadata().type = "dummy"`, `parseConfig` returning `this`. Confirm: (a) appears in `GET /api/backtest/conditions` automatically; (b) `ConditionConfig(type = "dummy")` builds and runs; (c) `DynamicStrategyBuilder.kt` was not touched.
