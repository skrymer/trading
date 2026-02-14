package com.skrymer.udgaard.data.model

import com.skrymer.udgaard.data.model.BreadthSymbol
import java.time.LocalDate

/**
 * Domain model for Breadth (Hibernate-independent)
 * Represents breadth data for either the entire market or a specific sector.
 *
 * The breadth symbol is stored in the database as two fields:
 * - symbolType: Either "MARKET" or "SECTOR"
 * - symbolValue: The identifier string (e.g., "FULLSTOCK" for market, "XLK" for sector)
 *
 * These fields are denormalized database columns that can be reconstructed into
 * a BreadthSymbol sealed class via the computed `symbol` property.
 */
data class Breadth(
  /** Database column: "MARKET" or "SECTOR" */
  val symbolType: String = "",
  /** Database column: Identifier string (e.g., "FULLSTOCK", "XLK", "XLF") */
  val symbolValue: String = "",
  val quotes: List<BreadthQuote> = emptyList(),
) {
  val symbol: BreadthSymbol?
    get() = BreadthSymbol.fromString(symbolValue)

  val inUptrend: Boolean
    get() = quotes.maxByOrNull { it.quoteDate }?.isInUptrend() ?: false

  val heatmap: Double
    get() = quotes.maxByOrNull { it.quoteDate }?.heatmap ?: 0.0

  val previousHeatmap: Double
    get() = quotes.maxByOrNull { it.quoteDate }?.previousHeatmap ?: 0.0

  val donkeyChannelScore: Int
    get() = quotes.lastOrNull()?.donkeyChannelScore ?: 0

  val name: String
    get() = symbol?.toDescription() ?: ""

  fun getDonkeyScoreByDate(date: LocalDate): Int =
    quotes.firstOrNull { it.quoteDate == date }?.donkeyChannelScore ?: 0

  fun getQuoteForDate(date: LocalDate?): BreadthQuote? =
    quotes.firstOrNull { date == it.quoteDate }

  fun getPreviousQuote(quote: BreadthQuote?): BreadthQuote? =
    if (quote == null) {
      null
    } else {
      quotes
        .sortedByDescending { it.quoteDate }
        .firstOrNull { it.quoteDate.isBefore(quote.quoteDate) }
    }

  fun getPreviousQuotes(
    date: LocalDate?,
    lookBack: Int,
  ): List<BreadthQuote> {
    val quote = getQuoteForDate(date)
    if (quote == null) {
      return emptyList()
    }

    val quotesSortedByDateAsc = quotes.sortedBy { it.quoteDate }
    val quoteIndex = quotesSortedByDateAsc.indexOf(quote)
    return if (quoteIndex < lookBack) {
      quotesSortedByDateAsc.subList(0, quoteIndex)
    } else {
      quotesSortedByDateAsc.subList(quoteIndex - lookBack, quoteIndex)
    }
  }
}
