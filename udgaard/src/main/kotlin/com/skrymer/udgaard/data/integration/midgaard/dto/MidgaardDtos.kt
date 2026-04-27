package com.skrymer.udgaard.data.integration.midgaard.dto

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
