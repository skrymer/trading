package com.skrymer.udgaard.model

import com.skrymer.udgaard.util.AssetMapper
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Represents a trade in a user's portfolio
 */
@Document(collection = "portfolio_trades")
data class PortfolioTrade(
    @Id
    val id: String? = null,
    val portfolioId: String,
    val symbol: String,

    // Instrument type
    val instrumentType: InstrumentType = InstrumentType.STOCK,

    // Options-specific fields (null for stocks)
    val optionType: OptionType? = null,
    val strikePrice: Double? = null,
    val expirationDate: LocalDate? = null,
    val contracts: Int? = null,
    val multiplier: Int = 100,

    // Option value components (optional)
    val entryIntrinsicValue: Double? = null,
    val entryExtrinsicValue: Double? = null,
    val exitIntrinsicValue: Double? = null,
    val exitExtrinsicValue: Double? = null,

    // Common fields
    val entryPrice: Double,
    val entryDate: LocalDate,
    val exitPrice: Double? = null,
    val exitDate: LocalDate? = null,
    val quantity: Int,
    val entryStrategy: String,
    val exitStrategy: String,
    val currency: String,
    val status: TradeStatus = TradeStatus.OPEN,
    /**
     * Optional underlying symbol to use for strategy evaluation.
     * Example: Trade TQQQ but use QQQ signals.
     * If null, will fall back to AssetMapper or use the trade symbol itself.
     */
    val underlyingSymbol: String? = null
) {
    /**
     * Get the symbol to use for strategy evaluation.
     * Priority: underlyingSymbol > AssetMapper > symbol
     */
    fun getStrategySymbol(): String {
        return underlyingSymbol ?: AssetMapper.getUnderlyingSymbol(symbol)
    }

    /**
     * Calculate profit for closed trades
     * Handles both stocks and options
     */
    val profit: Double?
        get() = when (instrumentType) {
            InstrumentType.STOCK, InstrumentType.LEVERAGED_ETF -> {
                if (status == TradeStatus.CLOSED && exitPrice != null) {
                    (exitPrice - entryPrice) * quantity
                } else null
            }
            InstrumentType.OPTION -> {
                if (status == TradeStatus.CLOSED && exitPrice != null) {
                    (exitPrice - entryPrice) * (contracts ?: quantity) * multiplier
                } else null
            }
        }

    /**
     * Calculate profit percentage for closed trades
     */
    val profitPercentage: Double?
        get() = when (instrumentType) {
            InstrumentType.STOCK, InstrumentType.LEVERAGED_ETF -> {
                if (status == TradeStatus.CLOSED && exitPrice != null) {
                    ((exitPrice - entryPrice) / entryPrice) * 100.0
                } else null
            }
            InstrumentType.OPTION -> {
                if (status == TradeStatus.CLOSED && exitPrice != null) {
                    ((exitPrice - entryPrice) / entryPrice) * 100.0
                } else null
            }
        }

    /**
     * Get the position size (capital at risk)
     */
    val positionSize: Double
        get() = when (instrumentType) {
            InstrumentType.STOCK, InstrumentType.LEVERAGED_ETF ->
                entryPrice * quantity
            InstrumentType.OPTION ->
                entryPrice * (contracts ?: quantity) * multiplier
        }

    /**
     * Current profit/loss for open trades (requires current price)
     */
    fun calculateUnrealizedProfitPercentage(currentPrice: Double): Double {
        return ((currentPrice - entryPrice) / entryPrice) * 100.0
    }

    /**
     * Current profit/loss amount for open trades
     */
    fun calculateUnrealizedProfit(currentPrice: Double): Double {
        return when (instrumentType) {
            InstrumentType.STOCK, InstrumentType.LEVERAGED_ETF ->
                (currentPrice - entryPrice) * quantity
            InstrumentType.OPTION ->
                (currentPrice - entryPrice) * (contracts ?: quantity) * multiplier
        }
    }

    /**
     * Check if option is expired
     */
    fun isExpired(): Boolean {
        return instrumentType == InstrumentType.OPTION &&
                expirationDate != null &&
                LocalDate.now().isAfter(expirationDate)
    }

    /**
     * Get days until expiration (for options)
     */
    fun daysToExpiration(): Long? {
        return if (instrumentType == InstrumentType.OPTION && expirationDate != null) {
            ChronoUnit.DAYS.between(LocalDate.now(), expirationDate)
        } else null
    }

    /**
     * Get time decay since entry (for options)
     * Returns the change in extrinsic value if both entry and exit values are available
     */
    fun timeDecay(): Double? {
        return if (instrumentType == InstrumentType.OPTION &&
            entryExtrinsicValue != null &&
            exitExtrinsicValue != null) {
            exitExtrinsicValue - entryExtrinsicValue
        } else null
    }
}

enum class TradeStatus {
    OPEN,
    CLOSED
}

enum class InstrumentType {
    STOCK,
    OPTION,
    LEVERAGED_ETF
}

enum class OptionType {
    CALL,
    PUT
}
