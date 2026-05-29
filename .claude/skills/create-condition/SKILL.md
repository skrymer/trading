---
name: create-condition
description: Author entry/exit conditions for the Udgaard backtest engine without introducing lookahead bias or perf regressions. Use when adding a new EntryCondition or ExitCondition under udgaard/src/main/kotlin/.../strategy/condition/, when extending an existing condition that iterates stock.orderBlocks / stock.earnings / stock.ovtlyrSignals / stock.quotes, or when writing a Kotlin entry/exit script in a strategy DSL config.
---

# Create a Backtest Condition

A condition is the public extension point of the backtest engine. The two failure modes to avoid: **lookahead bias** (referencing data invisible on the bar being evaluated — PR #34 inflated documented strategy edge by ~5pp on a 25-year walk-forward) and **hot-loop perf regressions** (conditions run up to ~26M times per backtest).

## Quick start

Implement `EntryCondition` (`evaluate(stock, quote, context): Boolean`) or `ExitCondition` (`shouldExit(stock, entryQuote, quote): Boolean`) under `udgaard/src/main/kotlin/com/skrymer/udgaard/backtesting/strategy/condition/{entry,exit}/`. Annotate `@Component`. Treat `quote.date` as the present; the bar is complete at close.

```kotlin
@Component
class MyCondition : EntryCondition {
  override fun evaluate(stock: Stock, quote: StockQuote, context: BacktestContext): Boolean {
    return stock.quotesInRange(quote.date.minusDays(40), quote.date).any { it.atr > quote.atr }
  }
  // description, getMetadata, evaluateWithDetails, parseConfig — see SCENARIOS.md anatomy
}
```

## Rules at a glance

Each rule has a one-paragraph explanation in [REFERENCE.md](REFERENCE.md) and a worked code example in [SCENARIOS.md](SCENARIOS.md).

### Code-level lookahead (correctness)

| # | Rule |
|---|---|
| L1 | Filter `stock.orderBlocks` / `earnings` / `ovtlyrSignals` by `quote.date` as the first predicate in the chain |
| L2 | `countTradingDaysBetween` coerces negative to zero — pair age filters with `startsBefore` / `isBefore(quote.date)` |
| L3 | Walk-back loops must re-apply the same lookahead guards at every iteration |
| L4 | `StockQuote` indicator fields (`atr`, EMAs, donchian, adx) are pre-computed at ingest — read, don't recompute |

### Semantic lookahead (point-in-time correctness)

| # | Rule |
|---|---|
| S1 | Rolling-window: include the current bar or not — document the choice in KDoc |
| S2 | For OBs, prefer `!triggerDate.isAfter(quote.date)` over `startsBefore` when the pattern's recognition timing matters (and always with `ageInDays = 0`) |
| S3 | Exit conditions: `entryQuote.closePriceEMA50` is the EMA *at entry date*, not today's running EMA |
| S4 | `earning.surprise` / `reportedEPS` are PIT-suspect (providers restate) — prefer reading price reaction post-`reportedDate` |
| S5 | Cross-sectional rank via `context` requires PIT-filtered peer set (peer existed and traded on `quote.date`) |

### Per-evaluation perf (hot-loop)

| # | Rule |
|---|---|
| P1 | Replace `stock.quotes.filter+sortedByDescending+take` with `stock.quotesInRange(from, to).subList(...)` — critical |
| P2 | Hoist time-independent filters out of walk-back loops; re-run only date-sensitive predicates inside |
| P3 | Use `getPreviousQuote(quote)` or `indexOnOrAfter(quote.date)`, never `indexOfFirst { it.date == quote.date }` |
| P4 | `.maxOf { it.atr }` directly on a sub-list; never `.map { it.atr }` first (boxes every Double) |
| P5 | Memoize `currentOvtlyrSignal` / `getNextEarningsDate` per `evaluate` if called repeatedly — they're O(K) scans |
| P6 | `.asSequence()` only on multi-step chains over `stock.quotes`; on sparse lists (`orderBlocks`, `earnings`) it's a wash |

## Canonical helpers (already lookahead-safe and O(log N))

| Helper | Returns |
|---|---|
| `OrderBlock.startsBefore(date)` / `endsAfter(date)` | strict-past / null-or-future-end |
| `Stock.withinOrderBlock(quote, ageDays, ...)` | candle overlaps active aged bearish OB |
| `Stock.getBullishOrderBlocks(date)` / `getBearishOrderBlocks(date)` | active OBs started before date |
| `Stock.quotesInRange(from, to)` | O(log N) sub-list view |
| `Stock.getPreviousQuote(quote)` / `getNextQuote(quote)` | O(log N) navigation |
| `Stock.currentOvtlyrSignal(asOf)` / `ovtlyrSignalOn(date)` | standing-state / exact-day |
| `Earning.isWithinDaysOf(date, days)` | bounded `0..days` forward-look |

Avoid `Stock.getActiveOrderBlocks()` inside a condition — filters only on `endDate == null`, no `startDate` guard.

## Test recipe

A new date-keyed-list-iterating condition needs at minimum:

1. Future-row filtered out (`plusDays(N)` row whose values would otherwise pass)
2. Boundary case (`date == quote.date` lands on documented side)
3. Walk-back path (if any) re-runs guard at every prior step
4. Rolling-window inclusion choice (fixture where in/out flips the verdict)
5. OB with `triggerDate > quote.date` excluded when `ageInDays = 0`

Templates in SCENARIOS.md.

## Scripts inherit every rule above

`ScriptEntryCondition` / `ScriptExitCondition` execute user Kotlin against `(stock, quote, entryQuote, context)` — no automatic guards. Every L/S/P rule applies inside a script verbatim.

**Before wiring a new entry condition into a strategy, screen it with `/condition-screen`** — a fast diagnostic pre-screen (forward-return lift, firing rate, parameter-sensitivity / ARS) that rejects structurally-unsound conditions at design time, before any backtest. For a script tunable, expose it as a `{{param}}` placeholder so the screen can sweep it.

## See also

- [REFERENCE.md](REFERENCE.md) — rule-by-rule explanation
- [SCENARIOS.md](SCENARIOS.md) — full condition anatomy + worked PR #34 refactor + per-pattern code snippets + test templates
