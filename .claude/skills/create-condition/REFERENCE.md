# Reference — Rule explanations

Companion to [SKILL.md](SKILL.md). One paragraph per rule, expanding the cheatsheet table.

## Code-level lookahead

### L1. Filter date-keyed lists by `quote.date` first

Whenever you iterate `stock.orderBlocks`, `stock.earnings`, or `stock.ovtlyrSignals`, the first filter must compare the row's date to `quote.date`. The canonical predicates are:

- `stock.orderBlocks` — `it.startsBefore(quote.date)` for "bar exists in past", or `!it.triggerDate.isAfter(quote.date)` for "pattern was recognized" (see S2).
- `stock.earnings` — `it.reportedDate != null && !it.reportedDate.isAfter(quote.date)`.
- `stock.ovtlyrSignals` — `!it.signalDate.isAfter(quote.date)`.

PR #34 filtered an OB only on `endDate`. A future-dated OB whose `[low, high]` covered the historical close leaked in. The fix: one line — `.filter { it.startsBefore(quote.date) }`.

### L2. `countTradingDaysBetween` coerces negative to zero

The helper's implementation is `(indexAfter(end) - indexAfter(start)).coerceAtLeast(0)`. If `start > end` (a future-dated row), it returns 0. An age check like `countTradingDaysBetween(triggerDate, quote.date) >= 0` therefore admits future rows silently. Pair age filters with an explicit `startsBefore` or `!isAfter(quote.date)` guard — or require a non-zero age (`>= 1`), which implicitly enforces past-only because zero-difference rows fail it.

### L3. Walk-back loops must re-apply the guards

If your condition does `var q = quote; while (...) { q = stock.getPreviousQuote(q); ... }` and re-evaluates state at each prior bar, every iteration must re-apply the same lookahead filters. The PR #34 fix had to patch its walk-back helper (`countBarsSinceBlocked`) for the same reason — calling the inner state-builder per prior date inherited the same missing guard.

### L4. `StockQuote` indicator fields are stored, not recomputed

`atr`, `closePriceEMA{5,10,20,50,100,200}`, `donchianHigh`, `donchianLow`, `adx` are computed once at ingest from the symbol's full history and stored on the row. Reading `quote.closePriceEMA20` always returns the same value for a given bar regardless of which backtest window contains it. Two consequences: never try to recompute them inline (you'll get inconsistent values vs the stored ones), and the first ~N bars per symbol carry seeding artifacts where N is the longest EMA period. A `MinimumHistoryDaysCondition` (or any age-since-listing filter) covers the warm-up case.

## Semantic lookahead

### S1. Rolling-window: include the current bar?

A condition asking "is today's high >= the prior 20-day max?" must exclude `quote` — otherwise on the day a new high prints the answer is tautologically true, and on every other day the comparison becomes "today's high >= some past max OR today's own high", which is also tautologically true. A condition asking "what's the z-score of today's volume?" should include `quote` as the value being scored, with stats computed over a past-only window. The choice is condition-specific. Document it in KDoc and pin it with a test (TR4 in the recipe).

### S2. OB `triggerDate` vs `startDate`

An `OrderBlock` carries `startDate` (the origin candle's date — sets `[low, high]`) and `triggerDate` (the confirmation bar where ROC momentum validates the pattern). `OrderBlockCalculator.kt:217-228` creates the row with both dates known. In a live data feed, the row would not have existed until `triggerDate` — between `startDate` and `triggerDate`, the bar at `startDate` just looked like any other bar. For conditions asking "is the price inside a *known* supply zone?" the gate must be `!triggerDate.isAfter(quote.date)`. The codebase commonly uses `startsBefore` paired with `countTradingDaysBetween(triggerDate, quote.date) >= 1`, which implicitly enforces `triggerDate < quote.date` because zero ages fail. With `ageInDays = 0`, neither filter enforces it — add the explicit `triggerDate` guard.

### S3. `entryQuote` fields are frozen at entry

`entryQuote.closePriceEMA50` is the EMA value **on the entry date**, not today's running EMA. Authors routinely confuse "trailing EMA crossed" (`quote.closePriceEMA50`, the running indicator) with "EMA at entry" (`entryQuote.closePriceEMA50`, a frozen reference). Both are valid; they answer different questions. Make the choice deliberate. Test names should disambiguate: `exits when running EMA50 crosses down through close` vs `exits when close falls X percent below entry-day EMA50`.

### S4. Earnings `surprise` and `reportedEPS` are PIT-suspect

Data providers sometimes overwrite reported numbers with restated figures post-announcement. A condition that filters `earning.surprise > 0` is silently betting that the stored figure matches what the market saw on `reportedDate`. For conditions depending on the announcement reaction, prefer reading the price reaction directly: `stock.quotesInRange(reportedDate, reportedDate.plusDays(3))` to score the post-announcement window from price alone. If you do read `surprise` / `reportedEPS`, document explicitly that you trust the ingest source's PIT guarantee, and consider gating on `daysSinceEarnings` to push the read further from the revision window.

### S5. Cross-sectional rank via `context` — PIT-filter the peer set

If a condition compares the current stock against today's universe (via `context: BacktestContext`), the peer set must reflect "stocks that existed and traded on `quote.date`" — not "stocks in the backtest's symbol list, including ones whose first quote is later than `quote.date`." Survivorship within a single backtest run is a real bias mode. Before ranking, filter peers by their first-quote-date.

## Per-evaluation perf

### P1. Sorted-lookback anti-pattern on `stock.quotes` (critical)

`stock.quotes` is already sorted ascending by date and held as an immutable in-memory list. The wrong idiom:

```kotlin
stock.quotes.filter { it.date <= quote.date }.sortedByDescending { it.date }.take(N).map { it.atr }
```

allocates three intermediate `ArrayList` objects per call (filter, sort, map) and unnecessarily sorts the entire filtered set before taking N. At 26M Pass-1 evaluations per backtest, this is ~78M short-lived ArrayLists. The right idiom is `Stock.quotesInRange(from, to)`, which does two binary searches (`indexOnOrAfter`, `indexAfter`) and returns a `subList` view — zero copies, zero sort. Three existing conditions use the wrong form: see issue #41 for the cleanup plan.

### P2. Hoist time-independent filters out of walk-back loops

`getPreviousQuote(...)` itself is fine in a loop (O(log N) per call, ~1300 comparisons over 100 iterations on a 6,500-bar history — negligible). What's not fine: rebuilding a multi-predicate `.filter` chain on `stock.orderBlocks` inside the loop, when only one or two predicates depend on the current iteration's date. Compute the time-independent subset once before the loop; evaluate only the date-sensitive predicates inside.

### P3. `indexOfFirst { it.date == quote.date }` is O(N)

Used in `PriceBelowEmaForDaysExit:49`. `stock.quotes` is sorted, so binary search applies — `indexOnOrAfter(quote.date)` is O(log N). For navigation, prefer `getPreviousQuote(quote)`. Only `indexOfFirst` if you need an integer index and there is no canonical helper; even then, do a binary search yourself rather than a linear scan.

### P4. `.map { it.atr }` before `maxOf` boxes every Double

`List<StockQuote>.map { it.atr }` produces a `List<Double>` of boxed doubles. `List<StockQuote>.maxOf { it.atr }` operates on the unboxed field inside the selector lambda. The map+max chain allocates a fresh list of N boxed Doubles plus the wrapping list; the direct `maxOf` allocates nothing beyond the closure. Same applies to `sumOf`, `minOf`, `averageBy*` patterns.

### P5. Memoize sparse-list standing-state derivations

`Stock.currentOvtlyrSignal(asOf)` and `Stock.getNextEarningsDate(afterDate)` each do `.filter { ... }.maxByOrNull { ... }` over a sparse list. At call sites inside Pass-1, each call is O(K) where K is the sparse list size — typically small per stock (~20 ovtlyr signals, ~40 earnings rows over a long history) but called per-bar per-symbol the cumulative scan count adds up. If a condition repeatedly derives the same standing state, memoize once in the calling code, or push the derived field into `StockQuote` at ingest time the way EMAs/ATR/Donchian already are.

### P6. `.asSequence()` is a wash on sparse lists

Sequence iterators avoid intermediate list allocations between filter steps, but they also cost iterator-dispatch overhead and partially defeat JIT inlining of lambdas. The break-even depends on list size and chain length: for `stock.quotes` (6,500 elements, multi-step chains) `asSequence` wins; for sparse lists like `orderBlocks` / `earnings` (tens of elements, often single-filter chains) the eager `Iterable` form is faster. Never finish a sequence chain with `.toList()` — that reintroduces the allocation you used the sequence to avoid.

## Two anti-patterns in existing code

Surfaced by the audit (issue #36); not bugs, but worth avoiding in new code:

- **Don't lean on the `startDate <= endDate` data invariant** to imply `startDate <= quote.date`. `OrderBlockBreakoutCondition.getNearestMitigatedOB` filters on `endDate <= quote.date` and relies on the invariant for the start-date guarantee. Brittle: a fixture violating the invariant breaks the chain silently. Add the explicit `startsBefore` / `triggerDate` guard.
- **Use `Stock.countTradingDaysBetween` for OB age, not `ChronoUnit.DAYS.between`.** `OrderBlockRejectionCondition` uses calendar days, which diverges from every other OB condition. Inconsistent ageing thresholds produce subtly different results in dense-vs-sparse OB regimes.
