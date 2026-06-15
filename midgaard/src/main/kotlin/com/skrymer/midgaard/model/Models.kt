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
    // The adjusted close (split AND dividend adjusted). OHLC are scaled to this basis.
    val close: Double,
    // The un-adjusted provider close — the absolute price level the point-in-time market cap reads,
    // since [close] carries the dividend adjustment that would bias an absolute-level product (ADR 0027).
    val rawClose: Double,
    val volume: Long,
)

data class Quote(
    val symbol: String,
    val date: LocalDate,
    val open: BigDecimal,
    val high: BigDecimal,
    val low: BigDecimal,
    val close: BigDecimal,
    // Un-adjusted provider close (ADR 0027). [close] is the split+dividend adjusted price; absolute-level
    // consumers (point-in-time market cap) read this. Null on bars stored before the raw-close re-store.
    val rawClose: BigDecimal? = null,
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
    val sma50: BigDecimal? = null,
    val sma150: BigDecimal? = null,
    val sma200: BigDecimal? = null,
    val high52Week: BigDecimal? = null,
    val low52Week: BigDecimal? = null,
    val relativeStrengthPercentile: BigDecimal? = null,
    val qualityPercentile: BigDecimal? = null,
    val indicatorSource: IndicatorSource = IndicatorSource.CALCULATED,
)

enum class IndicatorSource {
    ALPHAVANTAGE,
    EODHD,
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

/**
 * Point-in-time quarterly financial-statement line items for one fiscal period, the L1 reference data
 * the cross-sectional quality percentile is built from (ADR 0019). One record per `(symbol,
 * fiscalDateEnding)`; [filingDate] is the visibility key — a consumer may only see this record on a
 * trading date `≥ filingDate`, never on `fiscalDateEnding` (CONTEXT *Point-in-time fundamentals*).
 * Any line item may be null (the section was absent, or the provider omitted the field).
 */
data class Fundamental(
    val symbol: String,
    val fiscalDateEnding: LocalDate,
    val filingDate: LocalDate? = null,
    val grossProfit: BigDecimal? = null,
    val costOfRevenue: BigDecimal? = null,
    val totalRevenue: BigDecimal? = null,
    val operatingIncome: BigDecimal? = null,
    val netIncome: BigDecimal? = null,
    val totalAssets: BigDecimal? = null,
    val totalStockholderEquity: BigDecimal? = null,
    val totalCurrentAssets: BigDecimal? = null,
    val totalCurrentLiabilities: BigDecimal? = null,
    // Split-adjusted (current-basis) common shares outstanding — the share leg of the point-in-time
    // market cap (ADR 0027). EODHD reports this back-adjusted through every split. Null when not reported.
    val sharesOutstanding: Long? = null,
)

/**
 * One stock split for a symbol. [ratio] is new-shares-per-old (numerator / denominator of EODHD's
 * `"4.000000/1.000000"`): 4.0 for a 4:1 forward split, 0.125 for a 1:8 reverse. [exDate] is the first
 * session the price trades on the post-split basis. Feeds the cumulative split factor k(t) the
 * point-in-time market cap divides the raw close by (ADR 0027).
 */
data class Split(
    val symbol: String,
    val exDate: LocalDate,
    val ratio: Double,
)

data class Symbol(
    val symbol: String,
    val assetType: AssetType,
    val sector: String? = null,
    val sectorSymbol: String? = null,
    val delistedAt: LocalDate? = null,
    val cik: String? = null,
)

object SectorMapping {
    private val SECTOR_TO_SYMBOL =
        mapOf(
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

    fun toSectorSymbol(sectorName: String?): String? = sectorName?.uppercase()?.let { SECTOR_TO_SYMBOL[it] }

    /** The canonical 11 UPPERCASE GICS sector names — used by SectorIntegrityValidator and tests. */
    fun canonicalNames(): Set<String> = SECTOR_TO_SYMBOL.keys

    /** The canonical 11 sector ETF symbols (XLF, XLV, ...) — used by SectorIntegrityValidator. */
    fun canonicalSectorSymbols(): Set<String> = SECTOR_TO_SYMBOL.values.toSet()
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

data class LatestQuote(
    val symbol: String,
    val price: Double,
    val previousClose: Double,
    val change: Double,
    val changePercent: Double,
    val volume: Long,
    val timestamp: Long,
    val high: Double = 0.0,
    val low: Double = 0.0,
)

/**
 * One non-trading day for an exchange. Backfilled into `market_holidays`
 * via Flyway migration (sourced from EODHD's `/exchange-details/{exchange}`
 * snapshot at migration-write time). `IngestionService` consults the date
 * set to drop phantom provider bars stamped to closed market days.
 */
data class MarketHoliday(
    val exchange: String,
    val date: LocalDate,
    val name: String?,
    val type: String?,
)

/**
 * A third-party buy/sell call from ovtlyr.com for a symbol on a date. Stored sparsely —
 * a row exists only on days ovtlyr emitted a call. See the "Ovtlyr signal" glossary entry.
 */
data class OvtlyrSignal(
    val symbol: String,
    val signalDate: LocalDate,
    val signal: OvtlyrSignalType,
)

enum class OvtlyrSignalType {
    BUY,
    SELL,
}

/**
 * The gross (un-haircut) end-of-day yield, in percent, for a treasury maturity on a date — the
 * reference short rate the backtest engine credits idle cash at. `maturity` is the series key
 * (e.g. "US3M" for the 3-month T-bill that SGOV tracks). Stored gross: the SGOV expense haircut
 * is applied once downstream in Udgaard, never in the stored series. See ADR 0016.
 */
data class TreasuryYield(
    val maturity: String,
    val date: LocalDate,
    val yieldPct: Double,
)
