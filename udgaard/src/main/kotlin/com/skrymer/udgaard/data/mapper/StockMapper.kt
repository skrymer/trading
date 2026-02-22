package com.skrymer.udgaard.data.mapper

import com.skrymer.udgaard.data.model.Earning
import com.skrymer.udgaard.data.model.OrderBlock
import com.skrymer.udgaard.data.model.OrderBlockSensitivity
import com.skrymer.udgaard.data.model.OrderBlockType
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import com.skrymer.udgaard.jooq.tables.pojos.Earnings
import com.skrymer.udgaard.jooq.tables.pojos.OrderBlocks
import com.skrymer.udgaard.jooq.tables.pojos.StockQuotes
import com.skrymer.udgaard.jooq.tables.pojos.Stocks
import org.springframework.stereotype.Component

/**
 * Mapper between jOOQ POJOs and domain models
 */
@Component
class StockMapper {
  /**
   * Convert jOOQ Stock POJO to domain model
   * Requires associated quotes, order blocks, and earnings to be provided separately
   */
  fun toDomain(
    stock: Stocks,
    quotes: List<StockQuotes>,
    orderBlocks: List<OrderBlocks>,
    earnings: List<Earnings>,
  ): Stock =
    Stock(
      symbol = stock.symbol,
      sectorSymbol = stock.sectorSymbol,
      marketCap = stock.marketCap,
      quotes = quotes.map { toDomain(it) },
      orderBlocks = orderBlocks.map { toDomain(it) },
      earnings = earnings.map { toDomain(it) },
    )

  /**
   * Convert StockQuote jOOQ POJO to domain model
   */
  fun toDomain(quote: StockQuotes): StockQuote =
    StockQuote(
      symbol = quote.stockSymbol,
      date = quote.quoteDate,
      closePrice = quote.closePrice?.toDouble() ?: 0.0,
      openPrice = quote.openPrice?.toDouble() ?: 0.0,
      high = quote.highPrice?.toDouble() ?: 0.0,
      low = quote.lowPrice?.toDouble() ?: 0.0,
      closePriceEMA10 = quote.closePriceEma10?.toDouble() ?: 0.0,
      closePriceEMA20 = quote.closePriceEma20?.toDouble() ?: 0.0,
      closePriceEMA5 = quote.closePriceEma5?.toDouble() ?: 0.0,
      closePriceEMA50 = quote.closePriceEma50?.toDouble() ?: 0.0,
      closePriceEMA100 = quote.closePriceEma100?.toDouble() ?: 0.0,
      ema200 = quote.closePriceEma200?.toDouble() ?: 0.0,
      trend = quote.trend,
      atr = quote.atr?.toDouble() ?: 0.0,
      adx = quote.adx?.toDouble(),
      volume = quote.volume ?: 0L,
      donchianUpperBand = quote.donchianUpperBand?.toDouble() ?: 0.0,
    )

  /**
   * Convert OrderBlock jOOQ POJO to domain model
   */
  fun toDomain(orderBlock: OrderBlocks): OrderBlock =
    OrderBlock(
      low = orderBlock.lowPrice?.toDouble() ?: 0.0,
      high = orderBlock.highPrice?.toDouble() ?: 0.0,
      startDate = orderBlock.startDate,
      endDate = orderBlock.endDate,
      orderBlockType =
        when (orderBlock.type.uppercase()) {
          "BEARISH" -> OrderBlockType.BEARISH
          "BULLISH" -> OrderBlockType.BULLISH
          else -> OrderBlockType.BEARISH
        },
      volume = orderBlock.volume,
      volumeStrength = orderBlock.volumeStrength?.toDouble() ?: 0.0,
      sensitivity =
        when (orderBlock.sensitivity.uppercase()) {
          "HIGH" -> OrderBlockSensitivity.HIGH
          "LOW" -> OrderBlockSensitivity.LOW
          else -> null
        },
      rateOfChange = orderBlock.rateOfChange?.toDouble() ?: 0.0,
    )

  /**
   * Convert Earning jOOQ POJO to domain model
   */
  fun toDomain(earning: Earnings): Earning =
    Earning(
      symbol = earning.symbol ?: earning.stockSymbol,
      fiscalDateEnding = earning.fiscalDateEnding,
      reportedDate = earning.reportedDate,
      reportedEPS = earning.reportedeps?.toDouble(),
      estimatedEPS = earning.estimatedeps?.toDouble(),
      surprise = earning.surprise?.toDouble(),
      surprisePercentage = earning.surprisePercentage?.toDouble(),
      reportTime = earning.reportTime,
    )

  /**
   * Convert domain model to jOOQ Stock POJO
   */
  fun toPojo(stock: Stock): Stocks =
    Stocks(
      symbol = stock.symbol,
      sectorSymbol = stock.sectorSymbol,
      marketCap = stock.marketCap,
    )

  /**
   * Convert domain model to jOOQ StockQuote POJO
   */
  fun toPojo(quote: StockQuote): StockQuotes =
    StockQuotes(
      id = null, // Let database generate ID
      stockSymbol = quote.symbol,
      quoteDate = quote.date,
      openPrice = quote.openPrice.toBigDecimal(),
      closePrice = quote.closePrice.toBigDecimal(),
      highPrice = quote.high.toBigDecimal(),
      lowPrice = quote.low.toBigDecimal(),
      volume = quote.volume,
      atr = quote.atr.toBigDecimal(),
      adx = quote.adx?.toBigDecimal(),
      donchianHigh = null, // Not in domain
      donchianMid = null, // Not in domain
      donchianLow = null, // Not in domain
      donchianUpperBand = quote.donchianUpperBand.toBigDecimal(),
      donchianChannelScore = null, // Not in domain
      inUptrend = null, // Not in domain
      buySignal = null, // Not in domain
      sellSignal = null, // Not in domain
      closePriceEma5 = quote.closePriceEMA5.toBigDecimal(),
      closePriceEma10 = quote.closePriceEMA10.toBigDecimal(),
      closePriceEma20 = quote.closePriceEMA20.toBigDecimal(),
      closePriceEma50 = quote.closePriceEMA50.toBigDecimal(),
      closePriceEma100 = quote.closePriceEMA100.toBigDecimal(),
      closePriceEma200 = quote.ema200.toBigDecimal(),
      trend = quote.trend,
    )

  /**
   * Convert domain model to jOOQ OrderBlock POJO
   */
  fun toPojo(orderBlock: OrderBlock): OrderBlocks =
    OrderBlocks(
      id = null, // Let database generate ID
      stockSymbol = "", // Set by repository when saving
      type =
        when (orderBlock.orderBlockType) {
          OrderBlockType.BEARISH -> "BEARISH"
          OrderBlockType.BULLISH -> "BULLISH"
        },
      sensitivity =
        when (orderBlock.sensitivity) {
          OrderBlockSensitivity.HIGH -> "HIGH"
          OrderBlockSensitivity.LOW -> "LOW"
          null -> "LOW"
        },
      startDate = orderBlock.startDate,
      endDate = orderBlock.endDate,
      startPrice = orderBlock.low.toBigDecimal(), // Use low as start price
      endPrice = orderBlock.high.toBigDecimal(), // Use high as end price
      lowPrice = orderBlock.low.toBigDecimal(),
      highPrice = orderBlock.high.toBigDecimal(),
      volume = orderBlock.volume,
      volumeStrength = orderBlock.volumeStrength.toBigDecimal(),
      rateOfChange = orderBlock.rateOfChange.toBigDecimal(),
      isActive = orderBlock.endDate == null,
    )

  /**
   * Convert domain model to jOOQ Earning POJO
   */
  fun toPojo(earning: Earning): Earnings =
    Earnings(
      id = null, // Let database generate ID
      stockSymbol = earning.symbol,
      symbol = earning.symbol,
      fiscalDateEnding = earning.fiscalDateEnding,
      reportedDate = earning.reportedDate,
      reportedEps = earning.reportedEPS?.toBigDecimal(),
      reportedeps = earning.reportedEPS?.toBigDecimal(),
      estimatedEps = earning.estimatedEPS?.toBigDecimal(),
      estimatedeps = earning.estimatedEPS?.toBigDecimal(),
      surprise = earning.surprise?.toBigDecimal(),
      surprisePercentage = earning.surprisePercentage?.toBigDecimal(),
      reportTime = earning.reportTime,
    )
}
