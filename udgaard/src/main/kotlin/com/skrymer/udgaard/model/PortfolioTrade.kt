package com.skrymer.udgaard.model

import com.skrymer.udgaard.util.AssetMapper
import jakarta.persistence.*
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Represents a trade in a user's portfolio
 */
@Entity
@Table(
    name = "portfolio_trades",
    indexes = [
        Index(name = "idx_trade_portfolio", columnList = "portfolio_id"),
        Index(name = "idx_trade_symbol", columnList = "symbol"),
        Index(name = "idx_trade_entry_date", columnList = "entry_date"),
        Index(name = "idx_trade_status", columnList = "status")
    ]
)
data class PortfolioTrade(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "portfolio_id", nullable = false)
    val portfolioId: Long,

    @Column(length = 20, nullable = false)
    val symbol: String,

    // Instrument type
    @Enumerated(EnumType.STRING)
    @Column(name = "instrument_type", length = 20)
    val instrumentType: InstrumentType = InstrumentType.STOCK,

    // Options-specific fields (null for stocks)
    @Enumerated(EnumType.STRING)
    @Column(name = "option_type", length = 10)
    val optionType: OptionType? = null,

    @Column(name = "strike_price")
    val strikePrice: Double? = null,

    @Column(name = "expiration_date")
    val expirationDate: LocalDate? = null,

    val contracts: Int? = null,

    val multiplier: Int = 100,

    // Option value components (optional)
    @Column(name = "entry_intrinsic_value")
    val entryIntrinsicValue: Double? = null,

    @Column(name = "entry_extrinsic_value")
    val entryExtrinsicValue: Double? = null,

    @Column(name = "exit_intrinsic_value")
    val exitIntrinsicValue: Double? = null,

    @Column(name = "exit_extrinsic_value")
    val exitExtrinsicValue: Double? = null,

    // Common fields
    @Column(name = "entry_price", nullable = false)
    val entryPrice: Double,

    @Column(name = "entry_date", nullable = false)
    val entryDate: LocalDate,

    @Column(name = "exit_price")
    val exitPrice: Double? = null,

    @Column(name = "exit_date")
    val exitDate: LocalDate? = null,

    @Column(nullable = false)
    val quantity: Int,

    @Column(name = "entry_strategy", length = 100)
    val entryStrategy: String,

    @Column(name = "exit_strategy", length = 100)
    val exitStrategy: String,

    @Column(length = 10)
    val currency: String,

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    val status: TradeStatus = TradeStatus.OPEN,

    /**
     * Optional underlying symbol to use for strategy evaluation.
     * Example: Trade TQQQ but use QQQ signals.
     * If null, will fall back to AssetMapper or use the trade symbol itself.
     */
    @Column(name = "underlying_symbol", length = 20)
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
