package com.skrymer.udgaard.integration.ovtlyr.dto;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.skrymer.udgaard.model.MarketBreadth;
import com.skrymer.udgaard.model.Stock;

/**
 * Represents stock information comming from the Ovtlyr service. 
 */
public class OvtlyrStockInformation {
    @JsonProperty("resultDetail")
    private String result;
    private String stockName;
    private String sectorSymbol;
    @JsonProperty("lst_h")
    private List<OvtlyrStockQuote> quotes;

    public OvtlyrStockInformation(){}

    public Stock toModel(Optional<MarketBreadth> marketBreadth, Optional<MarketBreadth> sectorMarketBreadth, OvtlyrStockInformation spy) {
        return new Stock(
            this.stockName,
            this.sectorSymbol,
            this.quotes.stream().map(quote -> { 
                var sectorBreadthQuote = sectorMarketBreadth.flatMap(it -> it.getQuoteForDate(quote.getDate())); 
                var marketBreadthQuote = marketBreadth.flatMap(it -> it.getQuoteForDate(quote.getDate())); 
                return quote.toModel(this, marketBreadthQuote, sectorBreadthQuote, spy);
            }).toList()
        );
    }

    public String getResult() {
        return result;
    }

    public List<OvtlyrStockQuote> getQuotes() {
        return quotes;
    }

    public String getStockName() {
        return stockName;
    }

    public String getSectorSymbol() {
        return sectorSymbol;
    }

    public boolean isStockQuoteBullish(LocalDate quoteDate){
        if(quoteDate == null){
           throw new IllegalArgumentException("quoteDate can't be null");
        }

        Optional<OvtlyrStockQuote> currentStockQuote = quotes.stream()
            .filter(quote -> quoteDate.equals(quote.getDate()))
            .findFirst();
        Optional<OvtlyrStockQuote> previousStockQuote = quotes.stream()
            .filter(quote -> quoteDate.minusDays(1).equals(quote.getDate()))
            .findFirst();
        
        if(!currentStockQuote.isPresent() || !previousStockQuote.isPresent()){
            return false;
        }    
            
        var gettingMoreGreedy = currentStockQuote.get().getHeatmap() > previousStockQuote.get().getHeatmap();
            
        return currentStockQuote.get().isBullish() && gettingMoreGreedy;
    }

    @Override
    public String toString() {
        return stockName;
    }

    /**
     * Map nested objct stkDetail
     * @param stkDetail
     * 
     * Ovtlyr example payload:
     * {
     *  "stockSymbol": "NVDA",
     *  "symbolSEName": "NVDA",
     *  "stockName": "NVIDIA Corp",
     *  "sectorName": "Technology",
     *  "sectorSymbol": "XLK",
     *  "sectorHeatMap": 82.69744781359672,
     *  "stockExchange": "NASDAQ",
     *  "stockDesc": "NVIDIA is the world leader in accelerated computing.",
     *  "stockTypeID": 1,
     *  "isFavourite": true,
     *  "quotedate": "2025-06-05T00:00:00",
     *  "quotedateStr": "06-05-2025",
     *  "avgVolume": 216735185,
     *  "weekRange52_high": null,
     *  "weekRange52_low": null,
     *  "lastPrice": 139.99,
     *  "marketCap": 3415756000000,
     *  "peRatio": 44.4910516581,
     *  "gics_IndexTracked": null,
     *  "gics_ExpenseRation": null,
     *  "buySellDate": "2025-06-03T00:00:00",
     *  "buySellDateStr": "Jun 03, 2025",
     *  "buySellStatus": "Sell",
     *  "heatMap": 63.41708828753126,
     *  "oscilatorMovingUpDown": "Down",
     *  "sectorOscilatorMovingUpDown": "Down",
     *  "oscilator": 0.6341708828753125,
     *  "buySellFinalRegion": 1,
     *  "buySellFinalRegionLastChangedDate": "2025-05-02T00:00:00",
     *  "buySellFinalRegionLastChangedDateStr": "May 02, 2025",
     *  "isNotify": false,
     *  "masterKey_SectorSymbol": "XLK",
     *  "masterKey_Quotedate": "2025-06-05T00:00:00",
     *  "masterKey_Bull_per": 41.7910447761194,
     *  "masterKey_MarketBreadthArrow": "up",
     *  "masterKey_BuySellStatus_Spy": "Sell",
     *  "masterKey_BuySellQuoteDate_Spy": "2025-06-02T00:00:00",
     *  "masterKey_MarketTrend_Spy": "Bullish",
     *  "masterKey_Bull_per_FullStock": 40.2183039462637,
     *  "masterKey_MarketBreadthArrow_FullStock": "down",
     *  "notificationCondition": null
     * }
     */
    @JsonProperty("stkDetail")
    private void unpackNested(Map<String,Object> stkDetail) {
        this.stockName = (String)stkDetail.get("stockSymbol");
        this.sectorSymbol = (String)stkDetail.get("sectorSymbol");
    }

    /**
     * 
     * @param date
     * @return the last buy signal before the given date
     */
    public LocalDate getLastBuySignalBefore(LocalDate date) {
         return quotes.stream()
            // Only care about quotes with a buy signal
            .filter(it -> it.hasBuySignal())
            // Sort by quote date desc
            .sorted((a, b) ->  b.getDate().compareTo(a.getDate()))
            // Only look at quotes that are before this
            .filter(it -> it.getDate().isBefore(date) || it.getDate().isEqual(date))
            .map(it -> it.getDate())
            .findFirst()
            .orElse(null);
    }

    /**
     * 
     * @param date
     * @return the last sell signal before the given date
     */
    public LocalDate getLastSellSignalBefore(LocalDate date) {
        return quotes.stream()
            // Only care about quotes with a buy signal
            .filter(it -> it.hasSellSignal())
            // Sort by quote date desc
            .sorted((a, b) ->  b.getDate().compareTo(a.getDate()))
            // Only look at quotes that are before this
            .filter(it -> it.getDate().isBefore(date) || it.getDate().isEqual(date))
            .map(it -> it.getDate())
            .findFirst()
            .orElse(null);
    }

    public String getCurrentSignalFrom(LocalDate from) {
        var lastSellSignal = getLastSellSignalBefore(from);
        var lastBuySignal = getLastBuySignalBefore(from);
        
        return Objects.compare(lastBuySignal, lastSellSignal, Comparator.nullsLast(LocalDate::compareTo)) > 0 ? "Buy" : "Sell";
    }
}
