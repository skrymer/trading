package com.skrymer.udgaard.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** 
 * Represents the market breadth.
 * 
 * Market breadth refers to the number of stocks 
 * advancing versus declining in a market or index, 
 * indicating the overall health and direction of the market. 
 * 
 * Strong breadth shows widespread participation in a rally, 
 * while weak breadth suggests fewer stocks are driving the movement.
*/
@Document("marketBreadth")
public class MarketBreadth {
    @Id
    private MarketSymbol symbol;
    private List<MarketBreadthQuote> quotes;

    public MarketBreadth(){}

    public MarketBreadth(MarketSymbol symbol, List<MarketBreadthQuote> quotes) {
        this.symbol = symbol;
        this.quotes = quotes;
    }

    public MarketSymbol getSymbol() {
        return symbol;
    }

    public List<MarketBreadthQuote> getQuotes() {
        return quotes;
    }

    public Optional<MarketBreadthQuote> getQuoteForDate(LocalDate date){
        return quotes.stream().filter(it -> Objects.equals(date, it.getQuoteDate())).findFirst();
    }
}
