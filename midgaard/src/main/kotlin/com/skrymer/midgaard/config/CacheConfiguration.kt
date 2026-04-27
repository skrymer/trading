package com.skrymer.midgaard.config

import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Spring Cache abstraction for short-lived in-process caches.
 *
 * The `eodhdFundamentals` cache exists to dedup the two callers that hit
 * EODHD's `/api/fundamentals/` for each symbol (`getEarnings` +
 * `getCompanyInfo`). EODHD bills 10 weighted quota units per fundamentals
 * request — without dedup, a 3,128-symbol bulk ingest blows the 100k/day
 * cap (62k weighted just for fundamentals × 2 callers).
 *
 * `ConcurrentMapCacheManager` keeps everything in-memory with no eviction.
 * That's fine because each ingest cycle visits each symbol once, and the
 * cache is cleared at the start of every bulk run by `IngestionService`.
 */
@Configuration
@EnableCaching
class CacheConfiguration {
    @Bean
    fun cacheManager(): CacheManager = ConcurrentMapCacheManager(EODHD_FUNDAMENTALS_CACHE)

    companion object {
        const val EODHD_FUNDAMENTALS_CACHE = "eodhdFundamentals"
    }
}
