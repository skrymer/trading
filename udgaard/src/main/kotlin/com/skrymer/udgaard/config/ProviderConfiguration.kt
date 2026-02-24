package com.skrymer.udgaard.config

import com.skrymer.udgaard.data.integration.CompanyInfoProvider
import com.skrymer.udgaard.data.integration.FundamentalDataProvider
import com.skrymer.udgaard.data.integration.StockProvider
import com.skrymer.udgaard.data.integration.TechnicalIndicatorProvider
import com.skrymer.udgaard.data.integration.alphavantage.AlphaVantageClient
import com.skrymer.udgaard.data.integration.decorator.RateLimitedCompanyInfoProvider
import com.skrymer.udgaard.data.integration.decorator.RateLimitedFundamentalDataProvider
import com.skrymer.udgaard.data.integration.decorator.RateLimitedStockProvider
import com.skrymer.udgaard.data.integration.decorator.RateLimitedTechnicalIndicatorProvider
import com.skrymer.udgaard.data.integration.massive.MassiveClient
import com.skrymer.udgaard.data.service.RateLimiterService
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

/**
 * Provider configuration for data providers with rate limiting.
 *
 * Configures provider beans with rate-limited decorators to enforce
 * API rate limits for different data sources.
 */
@Configuration
class ProviderConfiguration(
  private val alphavantageClient: AlphaVantageClient,
  private val massiveClient: MassiveClient,
  private val rateLimiter: RateLimiterService,
  @Value("\${provider.alphavantage.requestsPerSecond:5}") private val avRequestsPerSecond: Int,
  @Value("\${provider.alphavantage.requestsPerMinute:75}") private val avRequestsPerMinute: Int,
  @Value("\${provider.alphavantage.requestsPerDay:500}") private val avRequestsPerDay: Int,
  @Value("\${provider.massive.requestsPerSecond:10}") private val massiveRequestsPerSecond: Int,
  @Value("\${provider.massive.requestsPerMinute:1000}") private val massiveRequestsPerMinute: Int,
  @Value("\${provider.massive.requestsPerDay:100000}") private val massiveRequestsPerDay: Int,
) {
  private val logger = LoggerFactory.getLogger(ProviderConfiguration::class.java)

  @PostConstruct
  fun init() {
    // Register AlphaVantage provider with rate limits
    logger.info(
      "Registering AlphaVantage provider with rate limits: " +
        "$avRequestsPerSecond/sec, $avRequestsPerMinute/min, $avRequestsPerDay/day",
    )
    rateLimiter.registerProvider(
      providerId = PROVIDER_ALPHAVANTAGE,
      requestsPerSecond = avRequestsPerSecond,
      requestsPerMinute = avRequestsPerMinute,
      requestsPerDay = avRequestsPerDay,
    )

    // Register Massive provider with rate limits
    logger.info(
      "Registering Massive provider with rate limits: " +
        "$massiveRequestsPerSecond/sec, $massiveRequestsPerMinute/min, $massiveRequestsPerDay/day",
    )
    rateLimiter.registerProvider(
      providerId = PROVIDER_MASSIVE,
      requestsPerSecond = massiveRequestsPerSecond,
      requestsPerMinute = massiveRequestsPerMinute,
      requestsPerDay = massiveRequestsPerDay,
    )
  }

  /**
   * Primary StockProvider bean with rate limiting.
   * Uses Massive API as the underlying provider for split-adjusted OHLCV data.
   */
  @Bean
  @Primary
  fun stockProvider(): StockProvider = RateLimitedStockProvider(
    delegate = massiveClient,
    providerId = PROVIDER_MASSIVE,
    rateLimiter = rateLimiter,
  )

  /**
   * Primary TechnicalIndicatorProvider bean with rate limiting.
   * Uses AlphaVantage as the underlying provider.
   */
  @Bean
  @Primary
  fun technicalIndicatorProvider(): TechnicalIndicatorProvider = RateLimitedTechnicalIndicatorProvider(
    delegate = alphavantageClient,
    providerId = PROVIDER_ALPHAVANTAGE,
    rateLimiter = rateLimiter,
  )

  /**
   * Primary FundamentalDataProvider bean with rate limiting.
   * Uses AlphaVantage as the underlying provider (earnings only).
   */
  @Bean
  @Primary
  fun fundamentalDataProvider(): FundamentalDataProvider = RateLimitedFundamentalDataProvider(
    delegate = alphavantageClient,
    providerId = PROVIDER_ALPHAVANTAGE,
    rateLimiter = rateLimiter,
  )

  /**
   * Primary CompanyInfoProvider bean with rate limiting.
   * Uses Massive API as the underlying provider (sector + market cap).
   */
  @Bean
  @Primary
  fun companyInfoProvider(): CompanyInfoProvider = RateLimitedCompanyInfoProvider(
    delegate = massiveClient,
    providerId = PROVIDER_MASSIVE,
    rateLimiter = rateLimiter,
  )

  companion object {
    const val PROVIDER_ALPHAVANTAGE = "alphavantage"
    const val PROVIDER_MASSIVE = "massive"
  }
}
