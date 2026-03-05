package com.skrymer.udgaard.portfolio.integration.broker

/**
 * Broker credentials abstraction.
 * Each broker has different authentication requirements.
 */
sealed class BrokerCredentials {
  /**
   * Interactive Brokers Flex Query credentials
   */
  data class IBKRCredentials(
    /**
     * Flex Query token (valid for up to 1 year, saved in settings)
     */
    val token: String,
    /**
     * Flex Query template ID (stored with portfolio)
     */
    val queryId: String,
  ) : BrokerCredentials()

  /**
   * Schwab API credentials (future)
   */
  data class SchwabCredentials(
    val apiKey: String,
    val apiSecret: String,
  ) : BrokerCredentials()

  /**
   * OAuth-based credentials (future)
   */
  data class OAuthCredentials(
    val accessToken: String,
    val refreshToken: String,
  ) : BrokerCredentials()
}
