package com.skrymer.udgaard.config

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.TimeUnit

@Configuration
@EnableCaching
class CacheConfig {
  @Bean
  fun cacheManager(): CacheManager {
    val cacheManager = CaffeineCacheManager()
    cacheManager.setCaffeine(
      Caffeine
        .newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(30, TimeUnit.MINUTES)
        .recordStats(),
    )
    cacheManager.setCacheNames(listOf("stocks", "backtests", "marketBreadth", "symbols"))
    cacheManager.registerCustomCache(
      "backtestResults",
      Caffeine
        .newBuilder()
        .maximumSize(10)
        .expireAfterAccess(1, TimeUnit.HOURS)
        .recordStats()
        .build(),
    )
    return cacheManager
  }
}
