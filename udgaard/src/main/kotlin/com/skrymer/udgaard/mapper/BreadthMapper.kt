package com.skrymer.udgaard.mapper

import com.skrymer.udgaard.domain.BreadthDomain
import com.skrymer.udgaard.domain.BreadthQuoteDomain
import com.skrymer.udgaard.jooq.tables.pojos.Breadth
import com.skrymer.udgaard.jooq.tables.pojos.BreadthQuotes
import org.springframework.stereotype.Component

/**
 * Mapper between jOOQ Breadth POJOs and domain models
 */
@Component
class BreadthMapper {
  /**
   * Convert jOOQ Breadth POJO to domain model
   */
  fun toDomain(
    breadth: Breadth,
    quotes: List<BreadthQuotes>,
  ): BreadthDomain =
    BreadthDomain(
      symbolType = breadth.symbolType ?: "",
      symbolValue = breadth.symbolValue ?: "",
      quotes = quotes.map { toDomain(it) },
    )

  /**
   * Convert BreadthQuote jOOQ POJO to domain model
   */
  fun toDomain(quote: BreadthQuotes): BreadthQuoteDomain =
    BreadthQuoteDomain(
      symbol = quote.symbolValue,
      quoteDate = quote.quoteDate,
      numberOfStocksWithABuySignal = quote.stocksWithBuySignal ?: 0,
      numberOfStocksWithASellSignal = quote.stocksWithSellSignal ?: 0,
      numberOfStocksInUptrend = quote.stocksInUptrend ?: 0,
      numberOfStocksInNeutral = quote.stocksInNeutral ?: 0,
      numberOfStocksInDowntrend = quote.stocksInDowntrend ?: 0,
      bullStocksPercentage = quote.bullStocksPercentage?.toDouble() ?: 0.0,
      ema_5 = quote.ema_5?.toDouble() ?: 0.0,
      ema_10 = quote.ema_10?.toDouble() ?: 0.0,
      ema_20 = quote.ema_20?.toDouble() ?: 0.0,
      ema_50 = quote.ema_50?.toDouble() ?: 0.0,
      heatmap = quote.heatmap?.toDouble() ?: 0.0,
      previousHeatmap = quote.previousHeatmap?.toDouble() ?: 0.0,
      donchianUpperBand = quote.donchianUpperBand?.toDouble() ?: 0.0,
      previousDonchianUpperBand = quote.previousDonchianUpperBand?.toDouble() ?: 0.0,
      donchianLowerBand = quote.donchianLowerBand?.toDouble() ?: 0.0,
      previousDonchianLowerBand = quote.previousDonchianLowerBand?.toDouble() ?: 0.0,
      donkeyChannelScore = quote.donkeyChannelScore ?: 0,
    )

  /**
   * Convert domain model to jOOQ Breadth POJO
   */
  fun toPojo(breadth: BreadthDomain): Breadth =
    Breadth(
      symbolType = breadth.symbolType,
      symbolValue = breadth.symbolValue,
    )

  /**
   * Convert domain model to jOOQ BreadthQuote POJO
   */
  fun toPojo(quote: BreadthQuoteDomain): BreadthQuotes =
    BreadthQuotes(
      symbolType = "", // Will be set by repository
      symbolValue = quote.symbol,
      quoteDate = quote.quoteDate,
      symbol = quote.symbol,
      stocksWithBuySignal = quote.numberOfStocksWithABuySignal,
      stocksWithSellSignal = quote.numberOfStocksWithASellSignal,
      stocksInUptrend = quote.numberOfStocksInUptrend,
      stocksInNeutral = quote.numberOfStocksInNeutral,
      stocksInDowntrend = quote.numberOfStocksInDowntrend,
      bullStocksPercentage = quote.bullStocksPercentage.toBigDecimal(),
      ema_5 = quote.ema_5.toBigDecimal(),
      ema_10 = quote.ema_10.toBigDecimal(),
      ema_20 = quote.ema_20.toBigDecimal(),
      ema_50 = quote.ema_50.toBigDecimal(),
      heatmap = quote.heatmap.toBigDecimal(),
      previousHeatmap = quote.previousHeatmap.toBigDecimal(),
      donchianUpperBand = quote.donchianUpperBand.toBigDecimal(),
      previousDonchianUpperBand = quote.previousDonchianUpperBand.toBigDecimal(),
      donchianLowerBand = quote.donchianLowerBand.toBigDecimal(),
      previousDonchianLowerBand = quote.previousDonchianLowerBand.toBigDecimal(),
      donkeyChannelScore = quote.donkeyChannelScore,
    )
}
