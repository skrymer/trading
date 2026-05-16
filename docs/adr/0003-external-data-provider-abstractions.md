# External data provider abstractions

External data flowing into Udgaard is fetched through **domain-scoped provider interfaces**. Each interface represents one coherent data domain (equity time series, options chains, FX rates) — *not* one vendor.

Services and tests depend on the interfaces. Concrete provider classes (`MidgaardClient` today) implement one or more provider interfaces and may additionally expose vendor-specific capabilities that don't fit a shared abstraction.

## The principle

Group methods by **what data they return**, not **which vendor they hit**.

```kotlin
// Domain-scoped — each interface covers one coherent data domain.
interface StockProvider {
  fun getDailyAdjustedTimeSeries(symbol: String): List<StockQuote>?
  fun getLatestQuote(symbol: String): LatestQuote?
  fun getEarnings(symbol: String): List<Earning>?
}

interface OptionsDataProvider {
  fun getHistoricalOptions(symbol: String, date: String?): List<OptionContract>?
  // ...
}

interface FxProvider {
  fun getExchangeRate(from: String, to: String): Double?
  fun getHistoricalExchangeRate(from: String, to: String, date: LocalDate): Double?
}
```

A vendor-named mega-interface (`MidgaardClient` with quotes + earnings + options + FX + catalogs) is the antipattern. It forces every future provider to either implement everything or stub-with-null large portions of the contract, which obscures genuine capability gaps and silently couples consumers to one vendor's surface.

## What goes on a provider interface

A method belongs on an interface when **the data it returns has a stable shape across plausible future vendors**. Earnings, OHLCV, FX rates, options chains all qualify — every vendor in the space (AlphaVantage, EODHD, Finnhub, Polygon) ships these as table-stakes capabilities.

A method that genuinely cannot be answered by a particular implementation returns `null`. Callers already handle null for outage cases, so "unsupported-by-this-provider" falls out naturally.

## What stays on the concrete class

Capabilities that are **vendor-specific** — no normalised shape across providers — stay on the concrete client:

- **Symbol catalog** (`MidgaardClient.getAllSymbols`, `getSymbolInfo`) — every vendor has a catalog, but catalog shapes (sector taxonomies, listing-status semantics, delisting timestamps) differ enough that a normalising interface would be lowest-common-denominator or fictional.

Callers needing vendor-specific data inject the concrete client by name and accept that swapping providers means touching that call site. Explicit coupling beats a fake-portable interface.

## Current state and known gaps

Existing provider interfaces:

- `StockProvider` — OHLCV + live quotes; `getEarnings` added by the change introducing this ADR.
- `OptionsDataProvider` — options chains.

Capabilities currently on `MidgaardClient` directly that should eventually move to a provider interface:

- **FX rates** (`getExchangeRate`, `getHistoricalExchangeRate`) → `FxProvider`. Not refactored yet because Midgaard is still the only FX source; the move pays off when a second source enters the picture.

The pattern is normative for new work. Existing drift is acknowledged and refactored opportunistically, not preemptively.

## Trade-off

Adding a method to a provider interface widens every implementation's surface. Worthwhile when the data is portable across vendors; wasteful and misleading when intrinsically vendor-specific — the interface ends up with one real implementation and several stubs, and any callsite that "depends on the interface" silently breaks if the provider changes.

The split costs nothing at the service-layer call site: `stockProvider.getEarnings(...)` reads identically to `midgaardClient.getEarnings(...)`. The difference shows up when the provider changes — interface-typed callers don't have to.

## Test discipline

Service-level and condition-level tests reference the provider interface and domain types — `StockProvider`, `LatestQuote`, `Earning`, `OptionContract`. Provider names appear only in tests that exercise the concrete provider class itself (`MidgaardClientTest`).
