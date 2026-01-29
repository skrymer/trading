package com.skrymer.udgaard.integration.broker

import com.skrymer.udgaard.domain.OptionTypeDomain
import java.time.LocalDate
import java.time.LocalTime

/**
 * Standardized trade format used as intermediate layer between broker APIs and our domain.
 * All broker adapters must convert their proprietary formats to this.
 */
data class StandardizedTrade(
  /**
   * Unique ID from broker
   */
  val brokerTradeId: String,
  /**
   * Ticker symbol
   */
  val symbol: String,
  /**
   * Execution date
   */
  val tradeDate: LocalDate,
  /**
   * Execution time (for roll detection)
   */
  val tradeTime: LocalTime?,
  /**
   * Number of shares/contracts
   */
  val quantity: Int,
  /**
   * Execution price
   */
  val price: Double,
  /**
   * BUY or SELL
   */
  val direction: TradeDirection,
  /**
   * OPEN or CLOSE
   */
  val openClose: OpenCloseIndicator,
  /**
   * STOCK, OPTION, or ETF
   */
  val assetType: AssetType,
  /**
   * Populated for options only
   */
  val optionDetails: OptionDetails?,
  /**
   * Links closing trade to opening trade (used for partial closes)
   */
  val linkedTradeId: String?,
  /**
   * Groups related trades (combo orders, rolls)
   */
  val relatedOrderId: String?,
  /**
   * Broker commission
   */
  val commission: Double?,
  /**
   * Net cash impact
   */
  val netAmount: Double,
  /**
   * Currency
   */
  val currency: String = "USD",
)

/**
 * Trade direction
 */
enum class TradeDirection {
  BUY,
  SELL,
}

/**
 * Open/Close indicator
 */
enum class OpenCloseIndicator {
  OPEN,
  CLOSE,
}

/**
 * Asset type
 */
enum class AssetType {
  STOCK,
  OPTION,
  ETF,
}

/**
 * Option-specific details
 */
data class OptionDetails(
  val underlyingSymbol: String,
  val optionType: OptionTypeDomain,
  val strike: Double,
  val expiry: LocalDate,
  val multiplier: Int = 100,
)
