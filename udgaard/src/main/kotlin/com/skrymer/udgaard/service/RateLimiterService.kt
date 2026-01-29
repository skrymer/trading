package com.skrymer.udgaard.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

/**
 * Multi-provider rate limiting service.
 *
 * Manages rate limiters for multiple data providers (AlphaVantage, Yahoo Finance, etc.)
 * Each provider has independent rate limits and tracking.
 *
 * Usage:
 * 1. Register providers: registerProvider(providerId, limits)
 * 2. Acquire permits: acquirePermit(providerId) - suspends until available
 * 3. Monitor usage: getProviderStats(providerId)
 */
@Service
class RateLimiterService {
  private val logger = LoggerFactory.getLogger(RateLimiterService::class.java)
  private val providers = ConcurrentHashMap<String, ProviderRateLimiter>()

  /**
   * Register a new provider with specific rate limits
   */
  fun registerProvider(
    providerId: String,
    requestsPerSecond: Int,
    requestsPerMinute: Int,
    requestsPerDay: Int,
  ) {
    if (providers.containsKey(providerId)) {
      logger.warn("Provider $providerId already registered, replacing with new configuration")
    }

    providers[providerId] = ProviderRateLimiter(
      providerId = providerId,
      requestsPerSecond = requestsPerSecond,
      requestsPerMinute = requestsPerMinute,
      requestsPerDay = requestsPerDay,
    )

    logger.info(
      "Registered provider $providerId with limits: $requestsPerSecond/sec, $requestsPerMinute/min, $requestsPerDay/day",
    )
  }

  /**
   * Acquire a permit to make an API request for a specific provider.
   * Suspends the coroutine until rate limits allow the request.
   *
   * @param providerId The provider identifier (e.g., "alphavantage")
   * @throws IllegalStateException if provider is not registered
   */
  suspend fun acquirePermit(providerId: String) {
    val rateLimiter = providers[providerId]
      ?: throw IllegalStateException("Provider $providerId not registered. Call registerProvider() first.")

    rateLimiter.acquirePermit()
  }

  /**
   * Get usage statistics for a specific provider
   */
  fun getProviderStats(providerId: String): ProviderRateLimitStats? = providers[providerId]?.getUsageStats()

  /**
   * Get usage statistics for all registered providers
   */
  fun getAllProviderStats(): Map<String, ProviderRateLimitStats> = providers.mapValues { it.value.getUsageStats() }
}
