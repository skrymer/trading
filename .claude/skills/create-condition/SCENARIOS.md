# Scenarios — Worked examples and snippets

Companion to [SKILL.md](SKILL.md) and [REFERENCE.md](REFERENCE.md). Copy-from-this code for the common condition-authoring patterns.

## Anatomy of an entry condition

The minimum surface. `@Component` is mandatory for DSL discovery.

```kotlin
@Component
class MyCondition(
  private val threshold: Double = 1.0,
  private val lookbackDays: Int = 20,
) : EntryCondition {

  override fun evaluate(stock: Stock, quote: StockQuote, context: BacktestContext): Boolean {
    val window = stock.quotesInRange(quote.date.minusDays(lookbackDays * 2L), quote.date)
    if (window.size < lookbackDays) return false
    val tail = window.subList(window.size - lookbackDays, window.size)
    return tail.maxOf { it.atr } > quote.atr * threshold
  }

  override fun description(): String = "ATR over $lookbackDays days exceeds today's by $threshold"

  override fun getMetadata() = ConditionMetadata(
    type = "myCondition",                       // unique key referenced by DSL configs
    displayName = "My Condition",
    description = "Human description shown by /api/backtest/conditions",
    parameters = listOf(
      ParameterMetadata("threshold", "Threshold", "number", threshold, min = 0.0, max = 10.0),
      ParameterMetadata("lookbackDays", "Lookback Days", "number", lookbackDays, min = 1, max = 100),
    ),
    category = "Custom",
  )

  override fun evaluateWithDetails(stock: Stock, quote: StockQuote, context: BacktestContext) =
    ConditionEvaluationResult(
      conditionType = "MyCondition",
      description = description(),
      passed = evaluate(stock, quote, context),
      actualValue = null,
      threshold = null,
      message = if (evaluate(stock, quote, context)) "passes" else "fails",
    )

  override fun parseConfig(parameters: Map<String, Any>): EntryCondition = MyCondition(
    threshold = parameters.numberOr("threshold", threshold),
    lookbackDays = parameters.intOr("lookbackDays", lookbackDays),
  )
}
```

Check the existing `getAvailableConditions` endpoint before naming your `type` string — collisions resolve arbitrarily.

## Worked example — the PR #34 bug

A condition that allows trades only when price has been above a bearish OB for N days. Strategy-neutral anonymisation of `AboveBearishOrderBlockCondition`.

### Before (buggy)

```kotlin
private fun getRelevantOrderBlocks(stock: Stock, quote: StockQuote): List<OrderBlock> =
  stock.orderBlocks
    .filter { it.orderBlockType == OrderBlockType.BEARISH }
    .filter { stock.countTradingDaysBetween(it.triggerDate, quote.date) >= ageInDays }
    .filter {
      val endDate = it.endDate
      endDate == null || endDate.isAfter(quote.date) || endDate.isEqual(quote.date)
    }
```

What looks correct: `endDate` covers "still active". Age filter covers "old enough".

What's wrong: nothing in the chain rejects an OB whose `startDate > quote.date`. A future OB whose `[low, high]` spans today's close is treated as active. Worse, `countTradingDaysBetween` returns 0 for that future row (negative coerced to zero), so any non-zero `ageInDays` requirement also fails to exclude it once the trigger date is past — though typically it's the start-date oversight that admits the future row.

### After (fixed)

```kotlin
private fun getRelevantOrderBlocks(stock: Stock, quote: StockQuote): List<OrderBlock> =
  stock.orderBlocks
    .filter { it.orderBlockType == OrderBlockType.BEARISH }
    .filter { it.startsBefore(quote.date) }                                    // <-- THE FIX
    .filter { stock.countTradingDaysBetween(it.triggerDate, quote.date) >= ageInDays }
    .filter {
      val endDate = it.endDate
      endDate == null || endDate.isAfter(quote.date) || endDate.isEqual(quote.date)
    }
```

One line restored agreement with every other OB-iterating condition.

### Regression tests

```kotlin
@Test
fun `future OB whose range covers current bar is filtered out`() {
  // Given a stock with one OB starting tomorrow whose price range covers today's close
  val today = LocalDate.of(2024, 1, 10)
  val futureOb = OrderBlock(
    startDate = today.plusDays(1),
    triggerDate = today.plusDays(1),
    endDate = null,
    low = 90.0, high = 110.0,
    orderBlockType = OrderBlockType.BEARISH,
  )
  val stock = stockOf(quotes = quotesIncluding(today, close = 100.0), orderBlocks = listOf(futureOb))

  // When the condition evaluates today's bar
  val result = condition.evaluate(stock, stock.getQuoteByDate(today)!!, BacktestContext.EMPTY)

  // Then the future OB must not influence the outcome
  assertThat(result).isTrue()
}

@Test
fun `boundary - OB starting exactly on the current bar is excluded`() {
  // Given an OB whose startDate equals quote.date
  val today = LocalDate.of(2024, 1, 10)
  val boundaryOb = OrderBlock(
    startDate = today, triggerDate = today, endDate = null,
    low = 90.0, high = 110.0, orderBlockType = OrderBlockType.BEARISH,
  )
  // ...

  // Then startsBefore is strict-less-than - today's OB is also filtered out
  assertThat(result).isTrue()
}

@Test
fun `walk-back loop does not admit a future OB at any prior bar`() {
  // Given a condition with a getPreviousQuote loop, verify the same guard applies at every step
  // (the PR #34 fix also patched countBarsSinceBlocked for this reason)
  // ...
}
```

## Pattern: iterating earnings, past-only

```kotlin
val mostRecentEarnings = stock.earnings
  .filter { it.reportedDate != null && !it.reportedDate.isAfter(quote.date) }
  .maxByOrNull { it.reportedDate!! }
```

`!isAfter(quote.date)` is the canonical `<=`. `reportedDate` is nullable (scheduled-but-not-yet-reported) — always null-out first.

For the explicit forward-look ("no earnings within next 7 days"), delegate to `stock.hasEarningsWithinDays(quote.date, days)` — it uses `Earning.isWithinDaysOf` which is bounded `0..days` by contract.

## Pattern: ovtlyr signals, standing state

```kotlin
override fun evaluate(stock: Stock, quote: StockQuote, context: BacktestContext): Boolean =
  stock.currentOvtlyrSignal(quote.date) == OvtlyrSignalType.BUY
```

Never iterate `stock.ovtlyrSignals` directly inside a condition unless `currentOvtlyrSignal(asOf)` / `ovtlyrSignalOn(date)` can't satisfy the requirement. Both helpers are already lookahead-safe.

## Pattern: quotes lookback, sub-list view (correct)

```kotlin
val window = stock.quotesInRange(quote.date.minusDays(lookbackDays * 2L), quote.date)
if (window.size < lookbackDays) return false
val tail = window.subList(window.size - lookbackDays, window.size)
val maxHigh = tail.maxOf { it.high }
```

Two binary searches + a sub-list view — zero allocations of `StockQuote` objects, zero sort. Use this in preference to `.filter { ... }.sortedByDescending { ... }.take(N)`.

The `lookbackDays * 2L` is calendar-day over-fetch to account for weekends/holidays inside the trading-bar window. Calibrate the multiplier to a comfortable floor for sparse-trading regimes.

## Pattern: walk-back loop with hoisted filters

```kotlin
override fun evaluate(stock: Stock, quote: StockQuote, context: BacktestContext): Boolean {
  // Hoist time-independent filters out of the loop body
  val candidates = stock.orderBlocks
    .filter { it.orderBlockType == OrderBlockType.BEARISH }
    .filter { sensitivity == null || it.sensitivity == sensitivity }
    .filter { !it.triggerDate.isAfter(quote.date) }                            // upper-bound for the entire walk-back

  var count = 0
  var current: StockQuote? = quote
  while (count < MAX_LOOKBACK && current != null) {
    val priorDate = current.date
    // Only re-evaluate the date-sensitive predicates inside the loop
    val activeNow = candidates.filter {
      it.startsBefore(priorDate) && (it.endDate == null || it.endDate.isAfter(priorDate))
    }
    if (!matches(activeNow, current)) return count >= consecutiveDays
    count++
    current = stock.getPreviousQuote(current)
  }
  return count >= consecutiveDays
}
```

`candidates` is computed once. The inner loop only re-runs the predicates that depend on `priorDate`.

## Pattern: anchoring a backward scan by index (use binary search)

```kotlin
// Wrong - O(N) linear scan
val currentIdx = stock.quotes.indexOfFirst { it.date == quote.date }

// Right - O(log N), and `getPreviousQuote` is usually all you need
val prev = stock.getPreviousQuote(quote)
```

If you need an integer index (e.g. to drive a for-loop):

```kotlin
val currentIdx = stock.indexOnOrAfter(quote.date)   // log N
```

## Test template — future-row + boundary + walk-back

```kotlin
class MyConditionTest {

  private val condition = MyCondition()
  private val today = LocalDate.of(2024, 1, 10)

  @Test
  fun `future-dated row is filtered out`() {
    // Given a stock with a row dated AFTER today that would otherwise pass
    val stock = stockOf(
      quotes = quotesIncluding(today),
      orderBlocks = listOf(orderBlockAt(startDate = today.plusDays(1))),
    )

    // When evaluating today
    val result = condition.evaluate(stock, stock.getQuoteByDate(today)!!, BacktestContext.EMPTY)

    // Then the future row does not influence the verdict
    assertThat(result).isTrue()
  }

  @Test
  fun `boundary - row dated exactly on current bar follows documented predicate`() {
    // Given a row with date == quote.date (strict-before predicate excludes it)
    val stock = stockOf(
      quotes = quotesIncluding(today),
      orderBlocks = listOf(orderBlockAt(startDate = today)),
    )

    // When evaluating today
    val result = condition.evaluate(stock, stock.getQuoteByDate(today)!!, BacktestContext.EMPTY)

    // Then the boundary row is excluded (startsBefore is strict)
    assertThat(result).isTrue()
  }

  @Test
  fun `walk-back loop re-applies guard at every prior step`() {
    // Given a future row that the loop would walk through if guards aren't re-applied
    // ...

    // When evaluating
    // ...

    // Then no iteration of the walk-back admits the future row
    // ...
  }
}
```

Use Given/When/Then comments in every test per project convention.

## Pre-merge checklist

- [ ] `@Component` annotation present
- [ ] Every iteration of `stock.orderBlocks` / `earnings` / `ovtlyrSignals` filters on a `quote.date` predicate first
- [ ] If using `countTradingDaysBetween`, paired with `startsBefore` / `isBefore(quote.date)` guard
- [ ] Walk-back helpers (if any) re-apply guards at every iteration
- [ ] Rolling-window inclusion choice (current bar in or out) documented in KDoc
- [ ] OB conditions with `ageInDays = 0` explicitly gate on `triggerDate <= quote.date`
- [ ] Lookback over `stock.quotes` uses `quotesInRange`, not `filter+sortedByDescending+take`
- [ ] No `.map { numericField }` before a reduce — use the direct reducer
- [ ] `parseConfig` round-trips every parameter exposed in `getMetadata`
- [ ] `evaluateWithDetails` returns the same passed/failed verdict as `evaluate` plus a human-readable `message`
- [ ] Tests cover the future-row, boundary, and (if applicable) walk-back paths
