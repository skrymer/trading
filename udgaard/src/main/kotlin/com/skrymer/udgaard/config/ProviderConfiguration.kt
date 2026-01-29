package com.skrymer.udgaard.config

import com.skrymer.udgaard.integration.FundamentalDataProvider
import com.skrymer.udgaard.integration.StockProvider
import com.skrymer.udgaard.integration.TechnicalIndicatorProvider
import com.skrymer.udgaard.integration.alphavantage.AlphaVantageClient
import com.skrymer.udgaard.integration.decorator.RateLimitedFundamentalDataProvider
import com.skrymer.udgaard.integration.decorator.RateLimitedStockProvider
import com.skrymer.udgaard.integration.decorator.RateLimitedTechnicalIndicatorProvider
import com.skrymer.udgaard.service.RateLimiterService
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
  private val rateLimiter: RateLimiterService,
  @Value("\${provider.alphavantage.requestsPerSecond:5}") private val avRequestsPerSecond: Int,
  @Value("\${provider.alphavantage.requestsPerMinute:75}") private val avRequestsPerMinute: Int,
  @Value("\${provider.alphavantage.requestsPerDay:500}") private val avRequestsPerDay: Int,
) {
  private val logger = LoggerFactory.getLogger(ProviderConfiguration::class.java)

  companion object {
    const val PROVIDER_ALPHAVANTAGE = "alphavantage"
  }

  @PostConstruct
  fun init() {
    // Register AlphaVantage provider with rate limits
    logger.info("Registering AlphaVantage provider with rate limits: $avRequestsPerSecond/sec, $avRequestsPerMinute/min, $avRequestsPerDay/day")
    rateLimiter.registerProvider(
      providerId = PROVIDER_ALPHAVANTAGE,
      requestsPerSecond = avRequestsPerSecond,
      requestsPerMinute = avRequestsPerMinute,
      requestsPerDay = avRequestsPerDay,
    )
  }

  /**
   * Primary StockProvider bean with rate limiting.
   * Uses AlphaVantage as the underlying provider.
   */
  @Bean
  @Primary
  fun stockProvider(): StockProvider = RateLimitedStockProvider(
    delegate = alphavantageClient,
    providerId = PROVIDER_ALPHAVANTAGE,
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
   * Uses AlphaVantage as the underlying provider.
   */
  @Bean
  @Primary
  fun fundamentalDataProvider(): FundamentalDataProvider = RateLimitedFundamentalDataProvider(
    delegate = alphavantageClient,
    providerId = PROVIDER_ALPHAVANTAGE,
    rateLimiter = rateLimiter,
  )
}
