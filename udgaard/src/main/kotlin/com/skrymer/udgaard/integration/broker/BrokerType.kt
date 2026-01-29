package com.skrymer.udgaard.integration.broker

/**
 * Supported broker types for portfolio integration
 */
enum class BrokerType {
  /**
   * Manual portfolio (no broker integration)
   */
  MANUAL,

  /**
   * Interactive Brokers
   */
  IBKR,

  // Future: SCHWAB, TD_AMERITRADE, ROBINHOOD, etc.
}
