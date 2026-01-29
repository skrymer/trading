package com.skrymer.udgaard.integration.broker

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
     * Flex Query token (6 hour expiry, user provides at sync time)
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
