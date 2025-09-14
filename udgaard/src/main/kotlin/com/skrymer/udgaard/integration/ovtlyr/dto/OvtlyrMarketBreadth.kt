package com.skrymer.udgaard.integration.ovtlyr.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.skrymer.udgaard.model.MarketBreadth
import com.skrymer.udgaard.model.MarketSymbol
import com.skrymer.udgaard.model.valueOf

class OvtlyrMarketBreadth {
    val resultDetail: String? = ""

    @JsonProperty("lst_h")
    val quotes: List<OvtlyrMarketBreadthQuote> = emptyList()

    fun toModel(stockInMarket: OvtlyrStockInformation?): MarketBreadth {
        val marketBreadthQuotes = quotes.stream()
            .map { it: OvtlyrMarketBreadthQuote? -> it!!.toModel(this, stockInMarket) }
            .toList()

        return MarketBreadth(getMarketSymbol(), marketBreadthQuotes)
    }

    override fun toString() =
        "Symbol: ${getMarketSymbol()}, Number of quotes: ${quotes.size}"

    fun getMarketSymbol(): MarketSymbol {
        return MarketSymbol.valueOf(quotes[0].symbol)
    }

    fun getPreviousQuote(quote: OvtlyrMarketBreadthQuote): OvtlyrMarketBreadthQuote {
        val sortedByDateAsc = quotes.sortedBy { it.quoteDate }
        val quoteIndex = sortedByDateAsc.indexOf(quote)

        return if(quoteIndex == -1 || quoteIndex == 0){
            return quote
        }
        else {
            quotes.get(quoteIndex - 1)
        }
    }

    fun getPreviousQuotes(quote: OvtlyrMarketBreadthQuote, lookback: Int): List<OvtlyrMarketBreadthQuote> {
        val sortedByDateAsc = quotes.sortedBy { it.quoteDate }
        val quoteIndex = sortedByDateAsc.indexOf(quote)

        return if(quoteIndex < lookback){
            sortedByDateAsc.subList(0, quoteIndex)
        } else {
            sortedByDateAsc.subList(quoteIndex - lookback, quoteIndex)
        }
    }
}

