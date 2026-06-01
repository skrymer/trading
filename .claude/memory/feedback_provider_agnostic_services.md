---
name: Provider-agnostic domain services
description: Domain services must not reference specific provider implementations or names; provider selection lives in @Configuration only
type: feedback
originSessionId: bf9ab2ae-922b-4ac8-b95e-79588cf96477
---
Domain services (e.g. `IngestionService`, `StockIngestionService`) must NOT contain provider-specific names in fields, qualifiers, branching logic, **or comments**. No `alphaVantageEarnings`, `eodhdOhlcv`, no "Midgaard outage", no "AlphaVantage rate limit" in narrative comments. Use neutral terms: "earnings", "ohlcv", "upstream outage", "provider rate limit". Same rule applies to ADRs and other docs that describe service behavior.

Provider replacement should be a single config change with zero code or comment edits.

**Why:** Direct user feedback during the EODHD migration (2026-04-27) — the previous abstraction (a `IngestSource` data class with a `when (ingestProviderName)` block selecting between alphaVantage/eodhd qualified beans inside `IngestionService`) leaked provider names everywhere and required code changes when adding/swapping providers.

**How to apply:**
- Use `@Primary` + `@ConditionalOnProperty` in `ProviderConfiguration` to pick which implementation backs each interface based on `app.ingest.provider`. Service constructor injects the interface (no `@Qualifier` for the toggled choice).
- Each provider acquires its own rate-limit permit internally (`rateLimiterService.acquirePermit("ownKey")` inside the provider's methods). The caller doesn't know which permit to ask for.
- The only `@Qualifier` in `IngestionService` should be for genuinely orthogonal pickers (e.g. the daily-update OHLCV path that's always Massive regardless of toggle).
- Naming: field `earnings: EarningsProvider`, not `alphaVantageEarnings`. Same for `ohlcv`, `indicators`, `companyInfo`.
- Decisions like "use local recompute for indicators" should be a separate config knob (e.g. `app.ingest.indicators=local|api`), not embedded in the provider-toggle branch — orthogonal concerns deserve orthogonal config.
