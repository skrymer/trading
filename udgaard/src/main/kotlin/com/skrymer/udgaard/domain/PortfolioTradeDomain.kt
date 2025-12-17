package com.skrymer.udgaard.domain

import com.skrymer.udgaard.model.TradeStatus
import java.time.LocalDate

/**
 * Domain model for PortfolioTrade (Hibernate-independent)
 * Represents a trade in a user's portfolio
 */
data class PortfolioTradeDomain(
  val id: Long? = null,
  val portfolioId: Long = 0,
  val symbol: String = "",
  val instrumentType: InstrumentTypeDomain = InstrumentTypeDomain.STOCK,
  val optionType: OptionTypeDomain? = null,
  val strikePrice: Double? = null,
  val expirationDate: LocalDate? = null,
  val contracts: Int? = null,
  val multiplier: Int = 100,
  val entryIntrinsicValue: Double? = null,
  val entryExtrinsicValue: Double? = null,
  val exitIntrinsicValue: Double? = null,
  val exitExtrinsicValue: Double? = null,
  val underlyingEntryPrice: Double? = null,
  val entryPrice: Double = 0.0,
  val entryDate: LocalDate = LocalDate.now(),
  val exitPrice: Double? = null,
  val exitDate: LocalDate? = null,
  val quantity: Int = 0,
  val entryStrategy: String = "",
  val exitStrategy: String = "",
  val currency: String = "USD",
  val status: TradeStatusDomain = TradeStatusDomain.OPEN,
  val underlyingSymbol: String? = null,
  val parentTradeId: Long? = null,
  val rolledToTradeId: Long? = null,
  val rollNumber: Int = 0,
  val originalEntryDate: LocalDate? = null,
  val originalCostBasis: Double? = null,
  val cumulativeRealizedProfit: Double? = null,
  val totalRollCost: Double? = null,
) {
  /**
   * Position size (total cost of entry)
   */
  val positionSize: Double
    get() =
      if (instrumentType == InstrumentTypeDomain.OPTION) {
        entryPrice * (contracts ?: quantity) * multiplier
      } else {
        entryPrice * quantity
      }

  /**
   * Profit/loss for this trade
   */
  val profit: Double?
    get() =
      exitPrice?.let { exit ->
        if (instrumentType == InstrumentTypeDomain.OPTION) {
          (exit - entryPrice) * (contracts ?: quantity) * multiplier
        } else {
          (exit - entryPrice) * quantity
        }
      }

  /**
   * Profit percentage for this trade
   */
  val profitPercentage: Double?
    get() = profit?.let { (it / positionSize) * 100.0 }

  /**
   * Get cumulative profit including all prior rolls in the chain
   */
  fun getCumulativeProfit(): Double? {
    val thisProfit = profit ?: return null
    return thisProfit + (cumulativeRealizedProfit ?: 0.0)
  }

  /**
   * Get cumulative return percentage based on original cost basis
   */
  fun getCumulativeReturnPercentage(): Double? {
    val cumulativeProfit = getCumulativeProfit() ?: return null
    val basis = originalCostBasis ?: positionSize
    return (cumulativeProfit / basis) * 100.0
  }
}

/**
 * Domain enum for instrument type
 */
enum class InstrumentTypeDomain {
  STOCK,
  OPTION,
  LEVERAGED_ETF
}

/**
 * Domain enum for option type
 */
enum class OptionTypeDomain {
  CALL,
  PUT
}

/**
 * Domain enum for trade status
 */
enum class TradeStatusDomain {
  OPEN,
  CLOSED,
  ;

  companion object {
    fun fromJpa(jpa: TradeStatus): TradeStatusDomain =
      when (jpa) {
        TradeStatus.OPEN -> OPEN
        TradeStatus.CLOSED -> CLOSED
      }
  }
}
