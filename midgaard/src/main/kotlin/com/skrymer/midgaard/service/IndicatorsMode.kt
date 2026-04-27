package com.skrymer.midgaard.service

/**
 * Where ATR/ADX values come from during initial ingest:
 *   - `LOCAL` (default) — recompute from raw OHLCV via `IndicatorCalculator`.
 *     Provider-neutral and split-safe.
 *   - `API` — fetch from the configured `IndicatorProvider`. Kept as a
 *     fallback; not recommended for EODHD because their `/api/technical/`
 *     endpoint silently mishandles split events.
 *
 * Driven by the `app.ingest.indicators` property. Spring's default enum
 * conversion is case-insensitive, so `local` / `LOCAL` / `Local` all parse.
 */
enum class IndicatorsMode {
    LOCAL,
    API,
}
