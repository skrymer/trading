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
      symbol = quote.symbol ?: "",
      quoteDate = quote.quoteDate ?: throw IllegalArgumentException("Quote date cannot be null"),
      numberOfStocksWithABuySignal = quote.stocksWithBuySignal ?: 0,
      numberOfStocksWithASellSignal = quote.stocksWithSellSignal ?: 0,
      numberOfStocksInUptrend = quote.stocksInUptrend ?: 0,
      numberOfStocksInNeutral = quote.stocksInNeutral ?: 0,
      numberOfStocksInDowntrend = quote.stocksInDowntrend ?: 0,
      bullStocksPercentage = quote.bullStocksPercentage ?: 0.0,
      ema_5 = quote.ema_5,
      ema_10 = quote.ema_10,
      ema_20 = quote.ema_20,
      ema_50 = quote.ema_50,
      heatmap = quote.heatmap,
      previousHeatmap = quote.previousHeatmap ?: 0.0,
      donchianUpperBand = quote.donchianUpperBand ?: 0.0,
      previousDonchianUpperBand = quote.previousDonchianUpperBand ?: 0.0,
      donchianLowerBand = quote.donchianLowerBand ?: 0.0,
      previousDonchianLowerBand = quote.previousDonchianLowerBand ?: 0.0,
      donkeyChannelScore = quote.donkeyChannelScore ?: 0,
    )

  /**
   * Convert domain model to jOOQ Breadth POJO
   */
  fun toPojo(breadth: BreadthDomain): Breadth =
    Breadth(
      id = null, // Let database generate ID
      symbolType = breadth.symbolType,
      symbolValue = breadth.symbolValue,
    )

  /**
   * Convert domain model to jOOQ BreadthQuote POJO
   */
  fun toPojo(quote: BreadthQuoteDomain): BreadthQuotes =
    BreadthQuotes(
      id = null, // Let database generate ID
      breadthId = null, // Set by repository
      symbol = quote.symbol,
      quoteDate = quote.quoteDate,
      stocksWithBuySignal = quote.numberOfStocksWithABuySignal,
      stocksWithSellSignal = quote.numberOfStocksWithASellSignal,
      stocksInUptrend = quote.numberOfStocksInUptrend,
      stocksInNeutral = quote.numberOfStocksInNeutral,
      stocksInDowntrend = quote.numberOfStocksInDowntrend,
      bullStocksPercentage = quote.bullStocksPercentage,
      ema_5 = quote.ema_5,
      ema_10 = quote.ema_10,
      ema_20 = quote.ema_20,
      ema_50 = quote.ema_50,
      heatmap = quote.heatmap,
      previousHeatmap = quote.previousHeatmap,
      donchianUpperBand = quote.donchianUpperBand,
      previousDonchianUpperBand = quote.previousDonchianUpperBand,
      donchianLowerBand = quote.donchianLowerBand,
      previousDonchianLowerBand = quote.previousDonchianLowerBand,
      donkeyChannelScore = quote.donkeyChannelScore,
    )
}
