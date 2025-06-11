package com.skrymer.udgaard.model;

/**
 * Entry strategy based on the ovtlyr 9 criteria:
 * 
 * Market 
 *  Market breadth is in an uptrend
 *  Spy is in an uptrend OK
 *  Spy has a buy signal OK
 * 
 * Sector:
 *  Sector breadth is in an uptrend  OK
 *  Sector is getting greeder OK
 * 
 * Stock: 
 *  is in an uptrend OK
 *  has a buy signal within the last 2 days OK
 *  is getting more greedy OK 
 */
public class Ovtlyr9EntryStrategy implements EntryStrategy {

    @Override
    public boolean test(StockQuote quote) {
        return 
            // The stock is in an uptrend
            quote.isInUptrend() && 
            // We have a buy signal
            quote.getLastBuySignal() != null &&
            // The buy signal is within the last 2 days of the quote
            quote.getLastBuySignal().isAfter(quote.getDate().minusDays(3)) && 
            // The stock heatmap is getting greeder
            quote.isGettingGreeder() && 
            // The sector is in an uptrend
            quote.sectorIsInUptrend() && 
            // The sector heatmap is getting greeder 
            quote.sectorIsGettingGreeder() && 
            // Spy has a buy signal
            quote.hasSpyBuySignal() &&
            // Spy is in an uptrend
            quote.isSpyInUptrend() &&
            // Market is in an uptrend
            quote.marketIsInUptrend();
    }

}
