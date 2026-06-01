---
name: Provider-neutral test descriptions
description: Tests should describe abstractions (StockProvider, live quote, stored bar), not concrete providers (Midgaard, Finnhub, Alpha Vantage).
type: feedback
originSessionId: bf9ab2ae-922b-4ac8-b95e-79588cf96477
---
When writing tests and test comments, describe behavior in terms of the abstractions the code actually depends on — `StockProvider`, "live quote", "stored quote", "the live bar matches the last stored bar" — not in terms of the concrete providers currently plugged in (Midgaard, Finnhub, Alpha Vantage, Ovtlyr).

**Why:** providers are swappable by design. Test descriptions naming them lock in assumptions that become stale the moment a provider is replaced. The test's invariant is about the interface contract, not about which vendor is behind it today.

**How to apply:** in `@Test` method names, test comments, and assertion messages, refer to `liveQuote`, `lastDbQuote`, `StockProvider`, "stored bar", "live bar". Vendor names belong only in integration/adapter tests that specifically exercise the vendor client (e.g., `MidgaardClientTest`).
