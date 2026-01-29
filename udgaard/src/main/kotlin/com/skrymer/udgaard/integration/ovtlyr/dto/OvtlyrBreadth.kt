package com.skrymer.udgaard.integration.ovtlyr.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.skrymer.udgaard.domain.BreadthDomain
import com.skrymer.udgaard.model.BreadthSymbol

/**
 * DTO for breadth data from Ovtlyr API.
 * Can represent either market breadth or sector breadth.
 */
class OvtlyrBreadth {
  val resultDetail: String? = ""

  @JsonProperty("lst_h")
  val quotes: List<OvtlyrBreadthQuote>? = null

  fun toModel(stockInSector: OvtlyrStockInformation?): BreadthDomain {
    val breadthQuotes = (quotes ?: emptyList()).map { it.toModel(this, stockInSector) }
    val breadthSymbol = getBreadthSymbol()

    // Extract symbolType and symbolValue from BreadthSymbol
    val (symbolType, symbolValue) = when (breadthSymbol) {
      is BreadthSymbol.Market -> "MARKET" to breadthSymbol.toIdentifier()
      is BreadthSymbol.Sector -> "SECTOR" to breadthSymbol.toIdentifier()
    }

    return BreadthDomain(
      symbolType = symbolType,
      symbolValue = symbolValue,
      quotes = breadthQuotes
    )
  }

  override fun toString() = "Symbol: ${getBreadthSymbol()}, Number of quotes: ${quotes?.size ?: 0}"

  fun getBreadthSymbol(): BreadthSymbol {
    val symbolStr = quotes?.firstOrNull()?.symbol
    return BreadthSymbol.fromString(symbolStr)
      ?: BreadthSymbol.Market() // Default to market if parsing fails
  }

  fun getPreviousQuote(quote: OvtlyrBreadthQuote): OvtlyrBreadthQuote {
    val quotesList = quotes ?: return quote
    val sortedByDateAsc = quotesList.sortedBy { it.quoteDate }
    val quoteIndex = sortedByDateAsc.indexOf(quote)

    return if (quoteIndex == -1 || quoteIndex == 0) {
      quote
    } else {
      sortedByDateAsc[quoteIndex - 1]
    }
  }

  fun getPreviousQuotes(
    quote: OvtlyrBreadthQuote,
    lookback: Int,
  ): List<OvtlyrBreadthQuote> {
    val quotesList = quotes ?: return emptyList()
    val sortedByDateAsc = quotesList.sortedBy { it.quoteDate }
    val quoteIndex = sortedByDateAsc.indexOf(quote)

    return if (quoteIndex < lookback) {
      sortedByDateAsc.subList(0, quoteIndex)
    } else {
      sortedByDateAsc.subList(quoteIndex - lookback, quoteIndex)
    }
  }
}
