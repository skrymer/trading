package com.skrymer.midgaard.model

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

data class RawBar(
    val symbol: String,
    val date: LocalDate,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long,
)

data class Quote(
    val symbol: String,
    val date: LocalDate,
    val open: BigDecimal,
    val high: BigDecimal,
    val low: BigDecimal,
    val close: BigDecimal,
    val volume: Long,
    val atr: BigDecimal? = null,
    val adx: BigDecimal? = null,
    val ema5: BigDecimal? = null,
    val ema10: BigDecimal? = null,
    val ema20: BigDecimal? = null,
    val ema50: BigDecimal? = null,
    val ema100: BigDecimal? = null,
    val ema200: BigDecimal? = null,
    val donchianUpper5: BigDecimal? = null,
    val indicatorSource: IndicatorSource = IndicatorSource.CALCULATED,
)

enum class IndicatorSource {
    ALPHAVANTAGE,
    CALCULATED,
}

data class Earning(
    val symbol: String,
    val fiscalDateEnding: LocalDate,
    val reportedDate: LocalDate? = null,
    val reportedEps: BigDecimal? = null,
    val estimatedEps: BigDecimal? = null,
    val surprise: BigDecimal? = null,
    val surprisePercentage: BigDecimal? = null,
    val reportTime: String? = null,
)

data class Symbol(
    val symbol: String,
    val assetType: AssetType,
    val sector: String? = null,
    val sectorSymbol: String? = null,
)

object SectorMapping {
    private val SECTOR_TO_SYMBOL = mapOf(
        "TECHNOLOGY" to "XLK",
        "FINANCIAL SERVICES" to "XLF",
        "HEALTHCARE" to "XLV",
        "ENERGY" to "XLE",
        "INDUSTRIALS" to "XLI",
        "CONSUMER CYCLICAL" to "XLY",
        "CONSUMER DEFENSIVE" to "XLP",
        "COMMUNICATION SERVICES" to "XLC",
        "BASIC MATERIALS" to "XLB",
        "REAL ESTATE" to "XLRE",
        "UTILITIES" to "XLU",
    )

    fun toSectorSymbol(sectorName: String?): String? =
        sectorName?.uppercase()?.let { SECTOR_TO_SYMBOL[it] }
}

enum class AssetType {
    STOCK,
    ETF,
    LEVERAGED_ETF,
    BOND_ETF,
    COMMODITY_ETF,
}

data class IngestionStatus(
    val symbol: String,
    val barCount: Int = 0,
    val lastBarDate: LocalDate? = null,
    val lastIngested: LocalDateTime? = null,
    val status: IngestionState = IngestionState.PENDING,
)

enum class IngestionState {
    PENDING,
    COMPLETE,
    PARTIAL,
    FAILED,
}

data class CompanyInfo(
    val sector: String?,
    val marketCap: Long?,
)

data class IngestionResult(
    val symbol: String,
    val success: Boolean,
    val barCount: Int = 0,
    val message: String? = null,
)
