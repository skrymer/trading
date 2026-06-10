package com.skrymer.udgaard.data.integration.midgaard.dto

import com.skrymer.udgaard.data.model.Earning
import com.skrymer.udgaard.data.model.Fundamental
import com.skrymer.udgaard.data.model.OvtlyrSignal
import com.skrymer.udgaard.data.model.OvtlyrSignalType
import com.skrymer.udgaard.data.model.StockQuote
import java.math.BigDecimal
import java.time.LocalDate

data class MidgaardQuoteDto(
  val symbol: String,
  val date: LocalDate,
  val open: BigDecimal,
  val high: BigDecimal,
  val low: BigDecimal,
  val close: BigDecimal,
  val volume: Long,
  val atr: BigDecimal?,
  val adx: BigDecimal?,
  val ema5: BigDecimal?,
  val ema10: BigDecimal?,
  val ema20: BigDecimal?,
  val ema50: BigDecimal?,
  val ema100: BigDecimal?,
  val ema200: BigDecimal?,
  val donchianUpper5: BigDecimal?,
  val sma50: BigDecimal? = null,
  val sma150: BigDecimal? = null,
  val sma200: BigDecimal? = null,
  val high52Week: BigDecimal? = null,
  val low52Week: BigDecimal? = null,
  val relativeStrengthPercentile: BigDecimal? = null,
  val qualityPercentile: BigDecimal? = null,
) {
  fun toStockQuote() = StockQuote(
    symbol = symbol,
    date = date,
    closePrice = close.toDouble(),
    openPrice = open.toDouble(),
    high = high.toDouble(),
    low = low.toDouble(),
    atr = atr?.toDouble() ?: 0.0,
    adx = adx?.toDouble(),
    closePriceEMA5 = ema5?.toDouble() ?: 0.0,
    closePriceEMA10 = ema10?.toDouble() ?: 0.0,
    closePriceEMA20 = ema20?.toDouble() ?: 0.0,
    closePriceEMA50 = ema50?.toDouble() ?: 0.0,
    closePriceEMA100 = ema100?.toDouble() ?: 0.0,
    ema200 = ema200?.toDouble() ?: 0.0,
    volume = volume,
    donchianUpperBand = donchianUpper5?.toDouble() ?: 0.0,
    // Null-preserving (no `?: 0.0`): an undefined SMA/52-week value must stay null so
    // conditions can treat "insufficient history" as a fail, not a spurious 0.0 comparison.
    sma50 = sma50?.toDouble(),
    sma150 = sma150?.toDouble(),
    sma200 = sma200?.toDouble(),
    high52Week = high52Week?.toDouble(),
    low52Week = low52Week?.toDouble(),
    relativeStrengthPercentile = relativeStrengthPercentile?.toDouble(),
    qualityPercentile = qualityPercentile?.toDouble(),
  )
}

data class MidgaardSymbolDto(
  val symbol: String,
  val assetType: String,
  val sector: String?,
  val sectorSymbol: String? = null,
  // Authoritative delisting date from midgaard's `symbols.delisted_at`. When
  // present, prefer this over the local 90-day-without-data heuristic in
  // `StockIngestionService` — the heuristic was a stop-gap that flagged
  // delisted symbols a quarter late and missed mid-quarter delistings.
  val delistedAt: LocalDate? = null,
)

data class MidgaardExchangeRateDto(
  val from: String,
  val to: String,
  val rate: Double,
  val date: LocalDate,
)

data class MidgaardLatestQuoteDto(
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

data class MidgaardEarningDto(
  val symbol: String,
  val fiscalDateEnding: LocalDate,
  val reportedDate: LocalDate? = null,
  val reportedEps: Double? = null,
  val estimatedEps: Double? = null,
  val surprise: Double? = null,
  val surprisePercentage: Double? = null,
  val reportTime: String? = null,
) {
  fun toEarning(): Earning =
    Earning(
      symbol = symbol,
      fiscalDateEnding = fiscalDateEnding,
      reportedDate = reportedDate,
      reportedEPS = reportedEps,
      estimatedEPS = estimatedEps,
      surprise = surprise,
      surprisePercentage = surprisePercentage,
      reportTime = reportTime,
    )
}

data class MidgaardFundamentalDto(
  val symbol: String,
  val fiscalDateEnding: LocalDate,
  val filingDate: LocalDate? = null,
  val grossProfit: Double? = null,
  val costOfRevenue: Double? = null,
  val totalRevenue: Double? = null,
  val operatingIncome: Double? = null,
  val netIncome: Double? = null,
  val totalAssets: Double? = null,
  val totalStockholderEquity: Double? = null,
  val totalCurrentAssets: Double? = null,
  val totalCurrentLiabilities: Double? = null,
) {
  fun toFundamental(): Fundamental =
    Fundamental(
      symbol = symbol,
      fiscalDateEnding = fiscalDateEnding,
      filingDate = filingDate,
      grossProfit = grossProfit,
      costOfRevenue = costOfRevenue,
      totalRevenue = totalRevenue,
      operatingIncome = operatingIncome,
      netIncome = netIncome,
      totalAssets = totalAssets,
      totalStockholderEquity = totalStockholderEquity,
      totalCurrentAssets = totalCurrentAssets,
      totalCurrentLiabilities = totalCurrentLiabilities,
    )
}

data class MidgaardOvtlyrSignalDto(
  val symbol: String,
  val signalDate: LocalDate,
  val signal: OvtlyrSignalType,
) {
  fun toOvtlyrSignal(): OvtlyrSignal =
    OvtlyrSignal(symbol = symbol, signalDate = signalDate, signal = signal)
}

/** One gross (un-haircut) treasury-yield point served by Midgaard — the idle-cash short rate (ADR 0016). */
data class MidgaardTreasuryYieldDto(
  val maturity: String,
  val date: LocalDate,
  val yieldPct: Double,
)
