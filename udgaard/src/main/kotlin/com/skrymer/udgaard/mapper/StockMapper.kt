package com.skrymer.udgaard.mapper

import com.skrymer.udgaard.domain.*
import com.skrymer.udgaard.jooq.tables.pojos.Earnings
import com.skrymer.udgaard.jooq.tables.pojos.OrderBlocks
import com.skrymer.udgaard.jooq.tables.pojos.StockQuotes
import com.skrymer.udgaard.jooq.tables.pojos.Stocks
import org.springframework.stereotype.Component
import com.skrymer.udgaard.jooq.enums.OrderBlocksSensitivity as JooqOrderBlocksSensitivity
import com.skrymer.udgaard.jooq.enums.OrderBlocksType as JooqOrderBlocksType

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
      ovtlyrPerformance = stock.ovtlyrPerformance,
      quotes = quotes.map { toDomain(it) },
      orderBlocks = orderBlocks.map { toDomain(it) },
      earnings = earnings.map { toDomain(it) },
    )

  /**
   * Convert StockQuote jOOQ POJO to domain model
   */
  fun toDomain(quote: StockQuotes): StockQuoteDomain =
    StockQuoteDomain(
      symbol = quote.stockSymbol ?: "",
      date = quote.quoteDate ?: throw IllegalArgumentException("Quote date cannot be null"),
      closePrice = quote.closePrice ?: 0.0,
      openPrice = quote.openPrice ?: 0.0,
      high = quote.highPrice ?: 0.0,
      low = quote.lowPrice ?: 0.0,
      heatmap = quote.heatmap,
      previousHeatmap = quote.previousHeatmap ?: 0.0,
      sectorHeatmap = quote.sectorHeatmap ?: 0.0,
      previousSectorHeatmap = quote.previousSectorHeatmap ?: 0.0,
      sectorIsInUptrend = quote.sectorIsInUptrend ?: false,
      sectorDonkeyChannelScore = quote.sectorDonkeyChannelScore ?: 0,
      signal = quote.signal,
      closePriceEMA10 = quote.closePriceEma10 ?: 0.0,
      closePriceEMA20 = quote.closePriceEma20 ?: 0.0,
      closePriceEMA5 = quote.closePriceEma5 ?: 0.0,
      closePriceEMA50 = quote.closePriceEma50 ?: 0.0,
      trend = quote.trend,
      lastBuySignal = quote.lastBuySignal,
      lastSellSignal = quote.lastSellSignal,
      spySignal = quote.spySignal,
      spyInUptrend = quote.spyInUptrend ?: false,
      spyHeatmap = quote.spyHeatmap ?: 0.0,
      spyPreviousHeatmap = quote.spyPreviousHeatmap ?: 0.0,
      spyEMA200 = quote.spyEma200 ?: 0.0,
      spySMA200 = quote.spySma200 ?: 0.0,
      spyEMA50 = quote.spyEma50 ?: 0.0,
      spyDaysAbove200SMA = quote.spyDaysAbove_200Sma ?: 0,
      marketAdvancingPercent = quote.marketAdvancingPercent ?: 0.0,
      marketIsInUptrend = quote.marketIsInUptrend ?: false,
      marketDonkeyChannelScore = quote.marketDonkeyChannelScore ?: 0,
      previousQuoteDate = quote.previousQuoteDate,
      sectorBreadth = quote.sectorBreadth ?: 0.0,
      sectorStocksInDowntrend = quote.sectorStocksInDowntrend ?: 0,
      sectorStocksInUptrend = quote.sectorStocksInUptrend ?: 0,
      sectorBullPercentage = quote.sectorBullPercentage ?: 0.0,
      atr = quote.atr,
      adx = quote.adx,
      volume = quote.volume,
      donchianUpperBand = quote.donchianUpperBand ?: 0.0,
      donchianUpperBandMarket = quote.donchianUpperBandMarket ?: 0.0,
      donchianUpperBandSector = quote.donchianUpperBandSector ?: 0.0,
      donchianLowerBandMarket = quote.donchianLowerBandMarket ?: 0.0,
      donchianLowerBandSector = quote.donchianLowerBandSector ?: 0.0,
    )

  /**
   * Convert OrderBlock jOOQ POJO to domain model
   */
  fun toDomain(orderBlock: OrderBlocks): OrderBlockDomain =
    OrderBlockDomain(
      low = orderBlock.lowPrice ?: 0.0,
      high = orderBlock.highPrice ?: 0.0,
      startDate = orderBlock.startDate ?: throw IllegalArgumentException("Start date cannot be null"),
      endDate = orderBlock.endDate,
      orderBlockType =
        when (orderBlock.type) {
          JooqOrderBlocksType.BEARISH -> OrderBlockType.BEARISH
          JooqOrderBlocksType.BULLISH -> OrderBlockType.BULLISH
          null -> OrderBlockType.BEARISH
        },
      volume = orderBlock.volume ?: 0L,
      volumeStrength = orderBlock.volumeStrength ?: 0.0,
      sensitivity =
        when (orderBlock.sensitivity) {
          JooqOrderBlocksSensitivity.HIGH -> OrderBlockSensitivity.HIGH
          JooqOrderBlocksSensitivity.LOW -> OrderBlockSensitivity.LOW
          null -> null
        },
      rateOfChange = orderBlock.rateOfChange ?: 0.0,
    )

  /**
   * Convert Earning jOOQ POJO to domain model
   */
  fun toDomain(earning: Earnings): EarningDomain =
    EarningDomain(
      symbol = earning.symbol,
      fiscalDateEnding = earning.fiscalDateEnding,
      reportedDate = earning.reportedDate,
      reportedEPS = earning.reportedeps,
      estimatedEPS = earning.estimatedeps,
      surprise = earning.surprise,
      surprisePercentage = earning.surprisePercentage,
      reportTime = earning.reportTime,
    )

  /**
   * Convert domain model to jOOQ Stock POJO
   */
  fun toPojo(stock: StockDomain): Stocks =
    Stocks(
      symbol = stock.symbol,
      ovtlyrPerformance = stock.ovtlyrPerformance,
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
      closePrice = quote.closePrice,
      openPrice = quote.openPrice,
      highPrice = quote.high,
      lowPrice = quote.low,
      heatmap = quote.heatmap,
      previousHeatmap = quote.previousHeatmap,
      sectorHeatmap = quote.sectorHeatmap,
      previousSectorHeatmap = quote.previousSectorHeatmap,
      sectorIsInUptrend = quote.sectorIsInUptrend,
      sectorDonkeyChannelScore = quote.sectorDonkeyChannelScore,
      signal = quote.signal,
      closePriceEma10 = quote.closePriceEMA10,
      closePriceEma20 = quote.closePriceEMA20,
      closePriceEma5 = quote.closePriceEMA5,
      closePriceEma50 = quote.closePriceEMA50,
      trend = quote.trend,
      lastBuySignal = quote.lastBuySignal,
      lastSellSignal = quote.lastSellSignal,
      spySignal = quote.spySignal,
      spyInUptrend = quote.spyInUptrend,
      spyHeatmap = quote.spyHeatmap,
      spyPreviousHeatmap = quote.spyPreviousHeatmap,
      spyEma200 = quote.spyEMA200,
      spySma200 = quote.spySMA200,
      spyEma50 = quote.spyEMA50,
      spyDaysAbove_200Sma = quote.spyDaysAbove200SMA,
      marketAdvancingPercent = quote.marketAdvancingPercent,
      marketIsInUptrend = quote.marketIsInUptrend,
      marketDonkeyChannelScore = quote.marketDonkeyChannelScore,
      previousQuoteDate = quote.previousQuoteDate,
      sectorBreadth = quote.sectorBreadth,
      sectorStocksInDowntrend = quote.sectorStocksInDowntrend,
      sectorStocksInUptrend = quote.sectorStocksInUptrend,
      sectorBullPercentage = quote.sectorBullPercentage,
      atr = quote.atr,
      adx = quote.adx,
      volume = quote.volume,
      donchianUpperBand = quote.donchianUpperBand,
      donchianUpperBandMarket = quote.donchianUpperBandMarket,
      donchianUpperBandSector = quote.donchianUpperBandSector,
      donchianLowerBandMarket = quote.donchianLowerBandMarket,
      donchianLowerBandSector = quote.donchianLowerBandSector,
    )

  /**
   * Convert domain model to jOOQ OrderBlock POJO
   */
  fun toPojo(orderBlock: OrderBlockDomain): OrderBlocks =
    OrderBlocks(
      id = null, // Let database generate ID
      lowPrice = orderBlock.low,
      highPrice = orderBlock.high,
      startDate = orderBlock.startDate,
      endDate = orderBlock.endDate,
      type =
        when (orderBlock.orderBlockType) {
          OrderBlockType.BEARISH -> JooqOrderBlocksType.BEARISH
          OrderBlockType.BULLISH -> JooqOrderBlocksType.BULLISH
        },
      volume = orderBlock.volume,
      volumeStrength = orderBlock.volumeStrength,
      sensitivity =
        when (orderBlock.sensitivity) {
          OrderBlockSensitivity.HIGH -> JooqOrderBlocksSensitivity.HIGH
          OrderBlockSensitivity.LOW -> JooqOrderBlocksSensitivity.LOW
          null -> null
        },
      rateOfChange = orderBlock.rateOfChange,
      source = null, // Not used in domain model
      stockSymbol = null, // Set by repository when saving
    )

  /**
   * Convert domain model to jOOQ Earning POJO
   */
  fun toPojo(earning: EarningDomain): Earnings =
    Earnings(
      id = null, // Let database generate ID
      symbol = earning.symbol,
      fiscalDateEnding = earning.fiscalDateEnding,
      reportedDate = earning.reportedDate,
      reportedeps = earning.reportedEPS,
      estimatedeps = earning.estimatedEPS,
      surprise = earning.surprise,
      surprisePercentage = earning.surprisePercentage,
      reportTime = earning.reportTime,
      stockSymbol = earning.symbol, // Use symbol as stock reference
    )
}
