package com.skrymer.udgaard.data.integration.decorator

import com.skrymer.udgaard.data.integration.TechnicalIndicatorProvider
import com.skrymer.udgaard.data.service.RateLimiterService
import java.time.LocalDate

/**
 * Rate-limited decorator for TechnicalIndicatorProvider.
 *
 * Wraps any TechnicalIndicatorProvider implementation and enforces rate limits
 * using suspend functions for true backpressure.
 *
 * @param delegate The underlying TechnicalIndicatorProvider to wrap
 * @param providerId Provider identifier (e.g., "alphavantage", "yahoo")
 * @param rateLimiter The rate limiter service managing provider-specific limits
 */
class RateLimitedTechnicalIndicatorProvider(
  private val delegate: TechnicalIndicatorProvider,
  private val providerId: String,
  private val rateLimiter: RateLimiterService,
) : TechnicalIndicatorProvider {
  override suspend fun getATR(
    symbol: String,
    interval: String,
    timePeriod: Int,
    minDate: LocalDate,
  ): Map<LocalDate, Double>? {
    rateLimiter.acquirePermit(providerId)
    return delegate.getATR(symbol, interval, timePeriod, minDate)
  }

  override suspend fun getADX(
    symbol: String,
    interval: String,
    timePeriod: Int,
    minDate: LocalDate,
  ): Map<LocalDate, Double>? {
    rateLimiter.acquirePermit(providerId)
    return delegate.getADX(symbol, interval, timePeriod, minDate)
  }
}
