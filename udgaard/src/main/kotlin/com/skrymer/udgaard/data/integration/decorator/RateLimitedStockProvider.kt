package com.skrymer.udgaard.data.integration.decorator

import com.skrymer.udgaard.data.integration.StockProvider
import com.skrymer.udgaard.data.model.StockQuote
import com.skrymer.udgaard.data.service.RateLimiterService
import java.time.LocalDate

/**
 * Rate-limited decorator for StockProvider.
 *
 * Wraps any StockProvider implementation and enforces rate limits
 * using suspend functions for true backpressure.
 *
 * @param delegate The underlying StockProvider to wrap
 * @param providerId Provider identifier (e.g., "alphavantage", "yahoo")
 * @param rateLimiter The rate limiter service managing provider-specific limits
 */
class RateLimitedStockProvider(
  private val delegate: StockProvider,
  private val providerId: String,
  private val rateLimiter: RateLimiterService,
) : StockProvider {
  override suspend fun getDailyAdjustedTimeSeries(
    symbol: String,
    outputSize: String,
    minDate: LocalDate,
  ): List<StockQuote>? {
    rateLimiter.acquirePermit(providerId)
    return delegate.getDailyAdjustedTimeSeries(symbol, outputSize, minDate)
  }
}
