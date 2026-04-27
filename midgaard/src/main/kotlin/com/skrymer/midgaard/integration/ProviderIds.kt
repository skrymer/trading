package com.skrymer.midgaard.integration

/**
 * Stable string identifiers for each data provider. Used as the rate-limiter
 * key inside each provider's methods, as the `app.ingest.provider` property
 * value, and as the lookup key in `RateLimiterService`.
 */
object ProviderIds {
    const val ALPHAVANTAGE = "alphavantage"
    const val EODHD = "eodhd"
    const val MASSIVE = "massive"
    const val FINNHUB = "finnhub"
    const val EDGAR = "edgar"
}
