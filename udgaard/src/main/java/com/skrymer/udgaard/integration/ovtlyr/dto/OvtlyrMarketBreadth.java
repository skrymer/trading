package com.skrymer.udgaard.integration.ovtlyr.dto;

import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.skrymer.udgaard.model.MarketBreadth;
import com.skrymer.udgaard.model.MarketBreadthQuote;
import com.skrymer.udgaard.model.MarketSymbol;

public class OvtlyrMarketBreadth {
    @JsonProperty("lst_h")
    private List<OvtlyrMarketBreadthQuote> quotes;

    public OvtlyrMarketBreadth() {}

    public MarketBreadth toModel(){
        var modelQuotes = quotes != null 
            ? quotes.stream().map(it -> it.toModel()).toList()
            : Collections.<MarketBreadthQuote>emptyList();     
        return new MarketBreadth(gMarketSymbol(),modelQuotes);
    }

    public List<OvtlyrMarketBreadthQuote> getQuotes() {
        return quotes;
    }

    @Override
    public String toString() {
        return "Symbol: %s, Number of quotes: %d".formatted(gMarketSymbol(), quotes != null ? quotes.size() : 0);
    }

    private MarketSymbol gMarketSymbol(){
        return quotes != null 
            ? MarketSymbol.valueOf(quotes.get(0).getSymbol()) 
            : MarketSymbol.UNK;
    }
}
