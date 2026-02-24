package com.skrymer.udgaard.data.integration.decorator

import com.skrymer.udgaard.data.integration.CompanyInfoProvider
import com.skrymer.udgaard.data.model.CompanyInfo
import com.skrymer.udgaard.data.service.RateLimiterService

/**
 * Rate-limited decorator for CompanyInfoProvider.
 *
 * Wraps any CompanyInfoProvider implementation and enforces rate limits
 * using suspend functions for true backpressure.
 *
 * @param delegate The underlying CompanyInfoProvider to wrap
 * @param providerId Provider identifier (e.g., "massive")
 * @param rateLimiter The rate limiter service managing provider-specific limits
 */
class RateLimitedCompanyInfoProvider(
  private val delegate: CompanyInfoProvider,
  private val providerId: String,
  private val rateLimiter: RateLimiterService,
) : CompanyInfoProvider {
  override suspend fun getCompanyInfo(symbol: String): CompanyInfo? {
    rateLimiter.acquirePermit(providerId)
    return delegate.getCompanyInfo(symbol)
  }
}
