package com.skrymer.udgaard.model;

import java.time.LocalDate;
import java.util.Optional;

/**
 * A stock quote.
 */
public class StockQuote {
    private String symbol;
    /**
     * The date when the stock quote was taken.
     */
    private LocalDate date;
    /**
     * The stock price at close.
     */
    private Double closePrice;
    /**
     * The stock price at open.
     */
    private Double openPrice;
    /**
     * The heatmap value of the stock. 
     * 
     * A value bewtween 0 and 100, 0 being max fear and 100 max greed.
     */
    private Double heatmap;
    /**
     * The previous heatmap value of the stock. 
     * 
     * A value bewtween 0 and 100, 0 being max fear and 100 max greed.
     */
    private Double previousHeatmap;
    /**
     * The heatmap value of the sector the stock belongs to. 
     * 
     * A value bewtween 0 and 100, 0 being max fear and 100 max greed.
     */
    private Double sectorHeatmap; 
    /**
     * The previous heatmap value of the sector the stock belongs to. 
     * 
     * A value bewtween 0 and 100, 0 being max fear and 100 max greed.
     */
    private Double previousSectorHeatmap;
    /**
     * true if the sector the stock belongs to is in an uptrend.
     */ 
    private boolean sectorIsInUptrend;
    /**
     * The ovtlyr Buy/Sell signal or null if neither.
     */ 
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
     * Is the stock in an Uptrend or Downtrend
     */
    private String trend;
    /**
     * The date of the last buy signal
     */
    private LocalDate lastBuySignal;
    /**
     * The date of the last sell signal
     */
    private LocalDate lastSellSignal;
    /**
     * Current SPY Buy/Sell signal. 
     */
    private String spySignal;
    /**
     * SPY is in an uptrend
     */
    private boolean spyIsInUptrend;
    /**
     * Market is in an uptrend
     */
    private boolean marketIsInUptrend;

    public StockQuote() {}

    public StockQuote(
        String symbol, 
        LocalDate date, 
        Double closePrice, 
        Double openPrice, 
        Double heatmap, 
        Double previousHeatmap, 
        Double sectorHeatmap, 
        Double previousSectorHeatmap,
        boolean sectorIsInUptrend, 
        String signal,
        Double closePrice_EMA10, 
        Double closePrice_EMA20, 
        Double closePrice_EMA5, 
        Double closePrice_EMA50,
        String trend,
        LocalDate lastBuySignal,
        LocalDate lastSellSignal,
        String spySignal,
        boolean spyIsInUptrend,
        boolean marketIsInUptrend 
    ) {
        this.symbol = symbol;
        this.date = date;
        this.closePrice = closePrice;
        this.openPrice = openPrice;
        this.heatmap = heatmap;
        this.sectorHeatmap = sectorHeatmap;
        this.previousHeatmap = previousHeatmap;
        this.previousSectorHeatmap = previousSectorHeatmap;
        this.sectorIsInUptrend = sectorIsInUptrend; 
        this.signal = signal;
        this.closePrice_EMA10 = closePrice_EMA10;
        this.closePrice_EMA20 = closePrice_EMA20;
        this.closePrice_EMA5 = closePrice_EMA5;
        this.closePrice_EMA50 = closePrice_EMA50;
        this.trend = trend;
        this.lastBuySignal = lastBuySignal;
        this.lastSellSignal = lastSellSignal;
        this.spySignal = spySignal;
        this.spyIsInUptrend = spyIsInUptrend;
        this.marketIsInUptrend = marketIsInUptrend;
    }

    public Double getClosePrice() {
        return closePrice;
    }

    public Double getClosePrice_EMA10() {
        return closePrice_EMA10;
    }

    public Double getClosePrice_EMA20() {
        return closePrice_EMA20;
    }

    public Double getClosePrice_EMA5() {
        return closePrice_EMA5;
    }

    public Double getClosePrice_EMA50() {
        return closePrice_EMA50;
    }

    public LocalDate getDate() {
        return date;
    }

    public Double getHeatmap() {
        return heatmap;
    }

    public Double getPreviousHeatmap() {
        return previousHeatmap;
    }

    public Double getSectorHeatmap() {
        return sectorHeatmap;
    }

    public Double getOpenPrice() {
        return openPrice;
    }

    public String getSignal() {
        return signal;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getTrend() {
        return trend;
    }

    public LocalDate getLastBuySignal() {
        return lastBuySignal;
    }

    public LocalDate getLastSellSignal() {
        return lastSellSignal;
    }

    public boolean isInUptrend() {
        return "Uptrend".equals(trend);
    }

    public boolean hasBuySignal() {
        return "Buy".equals(signal);
    }

    /**
     * 
     * @return true if this quotes heatmap value is greater than previous quotes heatmap value.
     */
    public boolean isGettingGreeder(){
        return Double.compare(heatmap, previousHeatmap) > 0;
    }

    public boolean sectorIsGettingGreeder(){
        return Double.compare(sectorHeatmap, previousSectorHeatmap) > 0;
    }

    /**
     * 
     * @return true if the sector the stock belongs to is in an uptrend.
     */
    public boolean sectorIsInUptrend() {
        return sectorIsInUptrend;
    }

    /**
     * 
     * @return true if SPY has a Buy signal.
     */
    public boolean hasSpyBuySignal() {
        return "Buy".equals(spySignal);
    }

    public boolean isSpyInUptrend() {
       return spyIsInUptrend;
    }

    public boolean marketIsInUptrend(){
        return marketIsInUptrend;
    }

    @Override
    public String toString() {
        return "Symbol: %s".formatted(symbol);
    }

}
