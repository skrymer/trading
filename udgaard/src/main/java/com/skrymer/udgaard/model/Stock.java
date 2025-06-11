package com.skrymer.udgaard.model;

import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;


/**
 * Represents a stock with a list of quotes.
 */
@Document(collection = "stocks")
public class Stock {
    @Id
    private String symbol;
    private String sectorSymbol;
    private List<StockQuote> quotes;

    public Stock() {}

    public Stock(String symbol, String sectorSymbol, List<StockQuote> quotes) {
        this.symbol = symbol;
        this.sectorSymbol = sectorSymbol;
        this.quotes = quotes;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getSectorSymbol() {
        return sectorSymbol;
    }

    public List<StockQuote> getQuotes() {
        return quotes;
    }

    /**
     * 
     * @param entryStrategy
     * @return quotes that matches the given entry strategy.
     */
    public List<StockQuote> getQuotesMatching(EntryStrategy entryStrategy) {
        return quotes.stream().filter(entryStrategy).toList();
    }
}
