package com.skrymer.midgaard.config

import com.github.benmanes.caffeine.cache.Caffeine
import com.skrymer.midgaard.integration.FxCacheNames
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.TimeUnit

/**
 * Spring Cache abstraction for in-process caches.
 *
 * - `eodhdFundamentals`: dedups EODHD `/api/fundamentals/{symbol}` calls between
 *   `getEarnings` and `getCompanyInfo` (10 weighted units per request → without
 *   dedup a 3,128-symbol bulk ingest blows EODHD's 100k/day cap). No TTL —
 *   cleared at the start of every bulk run by `IngestionService`.
 * - `fxCurrent` (1h TTL): current FX rates. Portfolio-stats UI tolerates 1h-stale
 *   FX (USD/AUD doesn't move > 0.5% in an hour).
 * - `fxHistoricalSeries` (24h TTL): full historical FX series per (from, to) pair.
 *   24h handles after-hours revisions of recent business days; older dates are
 *   immutable so refreshing daily is conservative. One cache entry per pair
 *   covers every date lookup → restart cost is one provider call per pair.
 *
 * Uses Caffeine because `ConcurrentMapCacheManager` doesn't support TTL.
 */
@Configuration
@EnableCaching
class CacheConfiguration {
    @Bean
    fun cacheManager(): CacheManager =
        CaffeineCacheManager().apply {
            registerCustomCache(EODHD_FUNDAMENTALS_CACHE, Caffeine.newBuilder().build())
            registerCustomCache(
                FxCacheNames.FX_CURRENT,
                Caffeine.newBuilder().expireAfterWrite(1, TimeUnit.HOURS).build(),
            )
            registerCustomCache(
                FxCacheNames.FX_HISTORICAL_SERIES,
                Caffeine.newBuilder().expireAfterWrite(24, TimeUnit.HOURS).build(),
            )
        }

    companion object {
        const val EODHD_FUNDAMENTALS_CACHE = "eodhdFundamentals"
    }
}
