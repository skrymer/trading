package com.skrymer.udgaard.mapper

import com.skrymer.udgaard.domain.*
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
  ): StockDomain =
    StockDomain(
      symbol = stock.symbol,
      sectorSymbol = stock.sectorSymbol,
      ovtlyrPerformance = null, // Not stored in JOOQ POJO
      quotes = quotes.map { toDomain(it) },
      orderBlocks = orderBlocks.map { toDomain(it) },
      earnings = earnings.map { toDomain(it) },
    )

  /**
   * Convert StockQuote jOOQ POJO to domain model
   */
  fun toDomain(quote: StockQuotes): StockQuoteDomain =
    StockQuoteDomain(
      symbol = quote.stockSymbol,
      date = quote.quoteDate,
      closePrice = quote.closePrice?.toDouble() ?: 0.0,
      openPrice = quote.openPrice?.toDouble() ?: 0.0,
      high = quote.highPrice?.toDouble() ?: 0.0,
      low = quote.lowPrice?.toDouble() ?: 0.0,
      heatmap = quote.heatmap?.toDouble() ?: 0.0,
      previousHeatmap = quote.previousHeatmap?.toDouble() ?: 0.0,
      sectorHeatmap = quote.sectorHeatmap?.toDouble() ?: 0.0,
      previousSectorHeatmap = quote.previousSectorHeatmap?.toDouble() ?: 0.0,
      sectorIsInUptrend = quote.sectorIsInUptrend ?: false,
      sectorDonkeyChannelScore = quote.sectorDonkeyChannelScore ?: 0,
      signal = quote.signal,
      closePriceEMA10 = quote.closePriceEma10?.toDouble() ?: 0.0,
      closePriceEMA20 = quote.closePriceEma20?.toDouble() ?: 0.0,
      closePriceEMA5 = quote.closePriceEma5?.toDouble() ?: 0.0,
      closePriceEMA50 = quote.closePriceEma50?.toDouble() ?: 0.0,
      closePriceEMA100 = quote.closePriceEma100?.toDouble() ?: 0.0,
      closePriceEMA200 = quote.closePriceEma200?.toDouble() ?: 0.0,
      trend = quote.trend,
      lastBuySignal = quote.lastBuySignal,
      lastSellSignal = quote.lastSellSignal,
      spySignal = quote.spySignal,
      spyInUptrend = quote.spyInUptrend ?: false,
      spyHeatmap = quote.spyHeatmap?.toDouble() ?: 0.0,
      spyPreviousHeatmap = quote.spyPreviousHeatmap?.toDouble() ?: 0.0,
      spyEMA200 = quote.spyEma200?.toDouble() ?: 0.0,
      spySMA200 = quote.spySma200?.toDouble() ?: 0.0,
      spyEMA50 = quote.spyEma50?.toDouble() ?: 0.0,
      spyDaysAbove200SMA = quote.spyDaysAbove_200sma ?: 0,
      marketAdvancingPercent = quote.marketAdvancingPercent?.toDouble() ?: 0.0,
      marketIsInUptrend = quote.marketIsInUptrend ?: false,
      marketDonkeyChannelScore = quote.marketDonkeyChannelScore ?: 0,
      marketBullPercentage = quote.marketBullPercentage?.toDouble() ?: 0.0,
      marketBullPercentage_10ema = quote.marketBullPercentage_10ema?.toDouble() ?: 0.0,
      previousQuoteDate = quote.previousQuoteDate,
      sectorBreadth = quote.sectorBreadth?.toDouble() ?: 0.0,
      sectorStocksInDowntrend = quote.sectorStocksInDowntrend ?: 0,
      sectorStocksInUptrend = quote.sectorStocksInUptrend ?: 0,
      sectorBullPercentage = quote.sectorBullPercentage?.toDouble() ?: 0.0,
      atr = quote.atr?.toDouble() ?: 0.0,
      adx = quote.adx?.toDouble(),
      volume = quote.volume ?: 0L,
      donchianUpperBand = quote.donchianUpperBand?.toDouble() ?: 0.0,
      donchianUpperBandMarket = quote.donchianUpperBandMarket?.toDouble() ?: 0.0,
      donchianUpperBandSector = quote.donchianUpperBandSector?.toDouble() ?: 0.0,
      donchianLowerBandMarket = quote.donchianLowerBandMarket?.toDouble() ?: 0.0,
      donchianLowerBandSector = quote.donchianLowerBandSector?.toDouble() ?: 0.0,
    )

  /**
   * Convert OrderBlock jOOQ POJO to domain model
   */
  fun toDomain(orderBlock: OrderBlocks): OrderBlockDomain =
    OrderBlockDomain(
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
  fun toDomain(earning: Earnings): EarningDomain =
    EarningDomain(
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
  fun toPojo(stock: StockDomain): Stocks =
    Stocks(
      symbol = stock.symbol,
      sectorSymbol = stock.sectorSymbol,
    )

  /**
   * Convert domain model to jOOQ StockQuote POJO
   */
  fun toPojo(quote: StockQuoteDomain): StockQuotes =
    StockQuotes(
      id = null, // Let database generate ID
      stockSymbol = quote.symbol,
      quoteDate = quote.date,
      openPrice = quote.openPrice.toBigDecimal(),
      closePrice = quote.closePrice.toBigDecimal(),
      highPrice = quote.high.toBigDecimal(),
      lowPrice = quote.low.toBigDecimal(),
      volume = quote.volume,
      adjustedClose = null, // Not in domain
      atr = quote.atr.toBigDecimal(),
      adx = quote.adx?.toBigDecimal(),
      ema5 = null, // Not in domain
      donchianHigh = null, // Not in domain
      donchianMid = null, // Not in domain
      donchianLow = null, // Not in domain
      donchianUpperBand = quote.donchianUpperBand.toBigDecimal(),
      donchianUpperBandMarket = quote.donchianUpperBandMarket.toBigDecimal(),
      donchianUpperBandSector = quote.donchianUpperBandSector.toBigDecimal(),
      donchianLowerBandMarket = quote.donchianLowerBandMarket.toBigDecimal(),
      donchianLowerBandSector = quote.donchianLowerBandSector.toBigDecimal(),
      donchianChannelScore = null, // Not in domain
      inUptrend = null, // Not in domain
      buySignal = null, // Not in domain
      sellSignal = null, // Not in domain
      heatmap = quote.heatmap.toBigDecimal(),
      previousHeatmap = quote.previousHeatmap.toBigDecimal(),
      stockHeatmap = null, // Not in domain
      previousStockHeatmap = null, // Not in domain
      sectorHeatmap = quote.sectorHeatmap.toBigDecimal(),
      previousSectorHeatmap = quote.previousSectorHeatmap.toBigDecimal(),
      sectorIsInUptrend = quote.sectorIsInUptrend,
      sectorDonkeyChannelScore = quote.sectorDonkeyChannelScore,
      signal = quote.signal,
      closePriceEma5 = quote.closePriceEMA5.toBigDecimal(),
      closePriceEma10 = quote.closePriceEMA10.toBigDecimal(),
      closePriceEma20 = quote.closePriceEMA20.toBigDecimal(),
      closePriceEma50 = quote.closePriceEMA50.toBigDecimal(),
      closePriceEma100 = quote.closePriceEMA100.toBigDecimal(),
      closePriceEma200 = quote.closePriceEMA200.toBigDecimal(),
      trend = quote.trend,
      lastBuySignal = quote.lastBuySignal,
      lastSellSignal = quote.lastSellSignal,
      spySignal = quote.spySignal,
      spyInUptrend = quote.spyInUptrend,
      spyHeatmap = quote.spyHeatmap.toBigDecimal(),
      spyPreviousHeatmap = quote.spyPreviousHeatmap.toBigDecimal(),
      spyEma200 = quote.spyEMA200.toBigDecimal(),
      spySma200 = quote.spySMA200.toBigDecimal(),
      spyEma50 = quote.spyEMA50.toBigDecimal(),
      spyDaysAbove_200sma = quote.spyDaysAbove200SMA,
      spyBuySignal = null, // Not in domain
      spySellSignal = null, // Not in domain
      marketHeatmap = null, // Not in domain
      previousMarketHeatmap = null, // Not in domain
      marketAdvancingPercent = quote.marketAdvancingPercent.toBigDecimal(),
      marketIsInUptrend = quote.marketIsInUptrend,
      marketDonkeyChannelScore = quote.marketDonkeyChannelScore,
      marketBullPercentage = quote.marketBullPercentage.toBigDecimal(),
      marketBullPercentage_10ema = quote.marketBullPercentage_10ema.toBigDecimal(),
      marketInUptrend = null, // Not in domain
      sectorInUptrend = null, // Not in domain
      marketDoncheyChannelScore = null, // Not in domain
      sectorDoncheyChannelScore = null, // Not in domain
      previousQuoteDate = quote.previousQuoteDate,
      sectorBreadth = quote.sectorBreadth.toBigDecimal(),
      sectorStocksInDowntrend = quote.sectorStocksInDowntrend,
      sectorStocksInUptrend = quote.sectorStocksInUptrend,
      sectorBullPercentage = quote.sectorBullPercentage.toBigDecimal(),
      sectorStocksAboveEma = null, // Not in domain
      sectorStocksCount = null, // Not in domain
      marketStocksAboveEma = null, // Not in domain
      marketStocksCount = null, // Not in domain
    )

  /**
   * Convert domain model to jOOQ OrderBlock POJO
   */
  fun toPojo(orderBlock: OrderBlockDomain): OrderBlocks =
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
  fun toPojo(earning: EarningDomain): Earnings =
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
