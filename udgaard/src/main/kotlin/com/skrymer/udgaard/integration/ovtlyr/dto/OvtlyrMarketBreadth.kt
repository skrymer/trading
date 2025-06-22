package com.skrymer.udgaard.integration.ovtlyr.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.skrymer.udgaard.model.MarketBreadth
import com.skrymer.udgaard.model.MarketSymbol
import com.skrymer.udgaard.model.valueOf

class OvtlyrMarketBreadth {
    @JsonProperty("lst_h")
    val quotes: List<OvtlyrMarketBreadthQuote> = emptyList()

    fun toModel(): MarketBreadth {
        val modelQuotes = quotes.stream()
            .map { it: OvtlyrMarketBreadthQuote? -> it!!.toModel() }
            .toList()

        return MarketBreadth(getMarketSymbol(), modelQuotes)
    }

    override fun toString() =
        "Symbol: ${getMarketSymbol()}, Number of quotes: ${quotes.size}"

    private fun getMarketSymbol(): MarketSymbol {
        return MarketSymbol.valueOf(quotes[0].symbol)
    }
}

