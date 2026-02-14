package com.skrymer.udgaard.data.integration.decorator

import com.skrymer.udgaard.data.integration.FundamentalDataProvider
import com.skrymer.udgaard.data.model.Earning
import com.skrymer.udgaard.data.model.SectorSymbol
import com.skrymer.udgaard.data.service.RateLimiterService

/**
 * Rate-limited decorator for FundamentalDataProvider.
 *
 * Wraps any FundamentalDataProvider implementation and enforces rate limits
 * using suspend functions for true backpressure.
 *
 * @param delegate The underlying FundamentalDataProvider to wrap
 * @param providerId Provider identifier (e.g., "alphavantage", "yahoo")
 * @param rateLimiter The rate limiter service managing provider-specific limits
 */
class RateLimitedFundamentalDataProvider(
  private val delegate: FundamentalDataProvider,
  private val providerId: String,
  private val rateLimiter: RateLimiterService,
) : FundamentalDataProvider {
  override suspend fun getEarnings(symbol: String): List<Earning>? {
    rateLimiter.acquirePermit(providerId)
    return delegate.getEarnings(symbol)
  }

  override suspend fun getSectorSymbol(symbol: String): SectorSymbol? {
    rateLimiter.acquirePermit(providerId)
    return delegate.getSectorSymbol(symbol)
  }
}
