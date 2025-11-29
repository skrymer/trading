package com.skrymer.udgaard.integration.ovtlyr.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.skrymer.udgaard.model.Breadth
import com.skrymer.udgaard.model.BreadthSymbol

/**
 * DTO for breadth data from Ovtlyr API.
 * Can represent either market breadth or sector breadth.
 */
class OvtlyrBreadth {
    val resultDetail: String? = ""

    @JsonProperty("lst_h")
    val quotes: List<OvtlyrBreadthQuote> = emptyList()

    fun toModel(stockInSector: OvtlyrStockInformation?): Breadth {
        val breadthQuotes = quotes
            .mapNotNull { it.toModel(this, stockInSector) }

        return Breadth(getBreadthSymbol(), breadthQuotes.toMutableList())
    }

    override fun toString() =
        "Symbol: ${getBreadthSymbol()}, Number of quotes: ${quotes.size}"

    fun getBreadthSymbol(): BreadthSymbol {
        val symbolStr = quotes.firstOrNull()?.symbol
        return BreadthSymbol.fromString(symbolStr)
            ?: BreadthSymbol.Market() // Default to market if parsing fails
    }

    fun getPreviousQuote(quote: OvtlyrBreadthQuote): OvtlyrBreadthQuote {
        val sortedByDateAsc = quotes.sortedBy { it.quoteDate }
        val quoteIndex = sortedByDateAsc.indexOf(quote)

        return if (quoteIndex == -1 || quoteIndex == 0) {
            quote
        } else {
            quotes[quoteIndex - 1]
        }
    }

    fun getPreviousQuotes(quote: OvtlyrBreadthQuote, lookback: Int): List<OvtlyrBreadthQuote> {
        val sortedByDateAsc = quotes.sortedBy { it.quoteDate }
        val quoteIndex = sortedByDateAsc.indexOf(quote)

        return if (quoteIndex < lookback) {
            sortedByDateAsc.subList(0, quoteIndex)
        } else {
            sortedByDateAsc.subList(quoteIndex - lookback, quoteIndex)
        }
    }
}
