package com.skrymer.udgaard.portfolio.integration.broker

import com.skrymer.udgaard.portfolio.integration.ibkr.IBKRAdapter
import org.springframework.stereotype.Component

/**
 * Factory for creating broker-specific adapters.
 * Uses strategy pattern to decouple broker selection from business logic.
 */
@Component
class BrokerAdapterFactory(
  private val ibkrAdapter: IBKRAdapter,
  // Future: private val schwabAdapter: SchwabAdapter,
) {
  /**
   * Get adapter for specified broker type
   *
   * @param brokerType - The broker type to get adapter for
   * @return Broker adapter implementation
   * @throws IllegalArgumentException if broker type not supported or is MANUAL
   */
  fun getAdapter(brokerType: BrokerType): BrokerAdapter =
    when (brokerType) {
      BrokerType.IBKR -> ibkrAdapter
      // Future: BrokerType.SCHWAB -> schwabAdapter
      BrokerType.MANUAL -> throw IllegalArgumentException("MANUAL portfolios don't use adapters")
    }
}
