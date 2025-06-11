package com.skrymer.udgaard.integration.ovtlyr.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.skrymer.udgaard.model.MarketBreadthQuote;
import com.skrymer.udgaard.model.StockQuote;

/**
 * Ovtlyr payload:
 *
 * {
 *   "stockSymbol": "NVDA",
 *   "quotedate": "2025-06-05T00:00:00",
 *   "quotedateStr": "2025-06-05",
 *   "quotedateStrShow": "Jun 05, 2025",
 *   "open": 142.17,
 *   "low": 138.83,
 *   "high": 144,
 *   "close": 139.99,
 *   "openStr": "$142.17",
 *   "lowStr": "$138.83",
 *   "highStr": "$144.00",
 *   "closeStr": "$139.99",
 *   "oscillator": 63.41708828753126,
 *   "final_region": 1,
 *   "final_region_combined": 1,
 *   "heatmap": 13.417088287531257,
 *   "heatMap_str": "G13",
 *   "final_calls": null,
 *   "tooltip": "Uptrend",
 *   "minClosePrice": 4.89323,
 *   "maxClosePrice": 149.43,
 *   "maxClosePriceWithAdd5Perc": 223.29324900000003,
 *   "gics_IndexTracked": null,
 *   "gics_ExpenseRation": null,
 *   "gics_sector": "Information Technology",
 *   "sectorSymbol": "XLK",
 *   "total_ClosePrice": 36826.9612,
 *   "bull_Total": 140,
 *   "bear_Total": 195,
 *   "bull_per": 41.7910447761194,
 *   "bull_EMA_5": 37.1546900350677,
 *   "bull_EMA_10": 40.0799899269599,
 *   "bull_EMA_20": 47.383792175294,
 *   "bull_EMA_50": 47.4596137058766,
 *   "lower": 25,
 *   "midpoint": 50,
 *   "upper": 75,
 *   "uptrend": 222,
 *   "uptrend_DifferenceWithPrevious": -5,
 *   "neutral": 59,
 *   "downtrend": 54,
 *   "downtrend_DifferenceWithPrevious": 3,
 *   "total": 335,
 *   "bar_min": 4.50157428993044,
 *   "bar_max": 195.933795071121,
 *   "closePrice_EMA5": 139.448852098839,
 *   "closePrice_EMA10": 137.267299816846,
 *   "closePrice_EMA20": 132.438548429477,
 *   "closePrice_EMA50": 124.876876126579,
 *   "bull_per_spy": 36.7032967032967,
 *   "bull_per_fullStock": 40.2183039462637,
 *   "net_weighted_FG": 12.1244495236215,
 *   "net_weighted_FG_display": 62.1244495236215,
 *   "green_display_value": 12.1244495236215,
 *   "red_display_value": 0,
 *   "masterKey_value": null
 * }
*/
public class OvtlyrStockQuote {
    @JsonProperty("stockSymbol")
    private String symbol;
    /**
     * The date when the stock quote was taken.
     */
    @JsonProperty("quotedate")
    private LocalDate date;
    /**
     * The stock price at close.
     */
    @JsonProperty("close")
    private Double closePrice;
    /**
     * The stock price at open.
     */
    @JsonProperty("open")
    private Double openPrice;
    /**
     * The heatmap of the stock. 
     * 
     * A value bewtween 0 and 100, 0 being max fear and 100 max greed.
     */
    @JsonProperty("oscillator")
    private Double heatmap; 
    /**
     * The heatmap of the sector the stock belongs to. 
     * 
     * A value bewtween 0 and 100, 0 being max fear and 100 max greed.
     */
    @JsonProperty("net_weighted_FG_display")
    private Double sectorHeatmap;
    /**
     * The ovtlyr buy/sell signal or null if neither.
     */ 
    @JsonProperty("final_calls")
    private String signal;
    /**
     * The 10 ema value on close
     */
    private Double closePrice_EMA10;
    /**
     * The 20 ema value on close
     */
    private Double closePrice_EMA20;
    /**
     * The 5 ema value on close
     */
    private Double closePrice_EMA5;
    /**
     * The 50 ema value on close
     */
    private Double closePrice_EMA50;
    /**  
     * Is the stock in an uptrend or downtrend
     */
    @JsonProperty("tooltip")
    private String trend;
    /**
     * The symbol of the sector the stock belongs to. E.g. NVDA belongs to XLK (THE TECHNOLOGY SELECT SECTOR SPDR FUND)
     */
    private String sectorSymbol;

    public StockQuote toModel(OvtlyrStockInformation stock, Optional<MarketBreadthQuote> marketBreadthQuote, Optional<MarketBreadthQuote> sectorMarketBreadthQuote, OvtlyrStockInformation spy){
        var previousQuote = stock.getQuotes().stream().filter(it -> this.date.isAfter(it.date)).findFirst();
        var previousHeatmap = previousQuote.map(it -> it.heatmap).orElse(Double.valueOf(0));
        var previousSectorHeatmap = previousQuote.map(it -> it.sectorHeatmap).orElse(Double.valueOf(0));
        var sectorIsInUptrend = sectorMarketBreadthQuote.map(it -> it.isInUptrend()).orElse(false);
        var lastBuySignal = stock.getLastBuySignalBefore(date);
        var lastSellSignal = stock.getLastSellSignalBefore(date);
        var spySignal = spy.getCurrentSignalFrom(date);
        var spyIsInUptrend = spy.getQuotes().stream().filter(it -> it.getDate().equals(date)).anyMatch(it -> it.isInUptrend());
        var marketIsInUptrend = marketBreadthQuote.map(it -> it.isInUptrend()).orElse(false);   
        
        return new StockQuote(
            this.symbol,
            this.date,
            this.closePrice,
            this.openPrice,
            this.heatmap,
            previousHeatmap,
            this.sectorHeatmap,
            previousSectorHeatmap,
            sectorIsInUptrend,
            this.signal,
            this.closePrice_EMA10,
            this.closePrice_EMA20,
            this.closePrice_EMA5,
            this.closePrice_EMA50,
            this.trend,
            lastBuySignal,
            lastSellSignal,
            spySignal,
            spyIsInUptrend,
            marketIsInUptrend
        );
    }

    public OvtlyrStockQuote() {}

    public Double getClosePrice() {
        return closePrice;
    }

    public Double getHeatmap() {
        return heatmap;
    }

    public String getSignal() {
        return signal;
    }

    public Double getClosePrice_EMA20() {
        return closePrice_EMA20;
    }

    public Double getClosePrice_EMA10() {
        return closePrice_EMA10;
    }

    public Double getClosePrice_EMA5() {
        return closePrice_EMA5;
    }

    public Double getClosePrice_EMA50() {
        return closePrice_EMA50;
    }

    public String getTrend() {
        return trend;
    }

    public LocalDate getDate() {
        return date;
    }

    public Double getOpenPrice() {
        return openPrice;
    }

    public String getSectorSymbol() {
        return sectorSymbol;
    }

    public boolean hasBuySignal(){
        return "Buy".equals(signal);
    }
    
    public boolean hasSellSignal() {
        return "Sell".equals(signal);
    }

    public boolean isInUptrend(){
        return "Uptrend".equals(trend);
    }

    /**
     * 
     * @return true if has buy signal and is in a uptrend,
     */
    public boolean isBullish(){
        return hasBuySignal() && isInUptrend(); 
    }

    @Override
    public String toString() {
        return "Symbol: %s Signal: %s Trend: %S".formatted(symbol, signal, trend);
    }
}
